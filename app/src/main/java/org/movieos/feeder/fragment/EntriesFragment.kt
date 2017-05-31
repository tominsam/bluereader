package org.movieos.feeder.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.movieos.feeder.FeederApplication
import org.movieos.feeder.MainActivity
import org.movieos.feeder.R
import org.movieos.feeder.databinding.EntriesFragmentBinding
import org.movieos.feeder.databinding.EntryRowBinding
import org.movieos.feeder.model.Entry
import org.movieos.feeder.model.SyncState
import org.movieos.feeder.utilities.RealmAdapter
import org.movieos.feeder.utilities.Settings
import org.movieos.feeder.utilities.SyncTask
import timber.log.Timber
import java.text.DateFormat

class EntriesFragment : DataBindingFragment<EntriesFragmentBinding>() {

    internal var adapter: RealmAdapter<Entry, EntryRowBinding>? = null
    internal var currentEntry = -1
    private val realm: Realm = Realm.getDefaultInstance()
    private var firstBeforePause: Int = 0
    private var lastBeforePause: Int = 0

    internal var viewType: Entry.ViewType = Entry.ViewType.UNREAD
    internal var entries: RealmResults<Entry>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FeederApplication.bus.register(this)
        if (savedInstanceState != null) {
            val viewType = savedInstanceState.getSerializable(VIEW_TYPE) as Entry.ViewType?
            this.viewType = viewType ?: this.viewType
        }

        adapter = object : RealmAdapter<Entry, EntryRowBinding>(EntryRowBinding::class.java, Entry::class.java) {
            override fun onBindViewHolder(holder: RealmAdapter.FeedViewHolder<EntryRowBinding>, instance: Entry) {
                holder.binding.entry = instance

                holder.itemView.setOnClickListener {
                    Entry.setUnread(context, realm, instance, false)
                    val fragment = DetailFragment.create(ids, instance.id)
                    fragment.setTargetFragment(this@EntriesFragment, 0)
                    fragmentManager
                            .beginTransaction()
                            .setTransition(TRANSIT_FRAGMENT_CLOSE)
                            .replace(R.id.main_content, fragment)
                            .addToBackStack(null)
                            .commit()
                }

                holder.binding.star.setOnClickListener { v ->
                    val newState = !v.isSelected
                    Entry.setStarred(context, realm, instance, newState)
                    v.isSelected = newState
                }
            }
        }

        setViewType(viewType, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(VIEW_TYPE, viewType)
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): EntriesFragmentBinding {
        val binding = EntriesFragmentBinding.inflate(inflater, container, false)
        binding.viewType = viewType
        binding.recyclerView.adapter = adapter
        binding.toolbar.inflateMenu(R.menu.entries_menu)
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_refresh -> {
                    item.isEnabled = false
                    SyncTask.sync(activity, true, false)
                }
                R.id.menu_logout -> {
                    Settings.saveCredentials(activity, null)
                    startActivity(Intent(activity, MainActivity::class.java))
                }
            }
            true
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.toolbar.menu?.findItem(R.id.menu_refresh)?.isEnabled = false
            SyncTask.sync(activity, true, false)
        }

        binding.stateUnread.setOnClickListener { setViewType(Entry.ViewType.UNREAD, true) }
        binding.stateStarred.setOnClickListener { setViewType(Entry.ViewType.STARRED, true) }
        binding.stateAll.setOnClickListener { setViewType(Entry.ViewType.ALL, true) }

        if (SyncState.latest(realm) == null) {
            // first run / first sync
            SyncTask.sync(activity, true, false)
        }

        return binding
    }

    override fun onResume() {
        super.onResume()
        displaySyncTime()

        // if we changed page in the detail view, scroll to minimally make that view visible.
        // To do this we tracked the first and last visible rows before we left (because in this
        // method we're not laid out yet), and will assume this has not changed. If the phone
        // has rotated or resized we'll guess wrong here.
        val binding = binding ?: return
        if (currentEntry >= 0 && firstBeforePause >= 0 && lastBeforePause >= 0) {
            if (currentEntry < firstBeforePause) {
                binding.recyclerView.scrollToPosition(currentEntry)
            } else if (currentEntry > lastBeforePause) {
                binding.recyclerView.scrollToPosition(currentEntry - (lastBeforePause - firstBeforePause))
            }
            currentEntry = -1
        }

        setViewType(viewType, false)
    }

    override fun onPause() {
        super.onPause()
        if (binding != null) {
            val manager = binding!!.recyclerView.layoutManager as LinearLayoutManager
            firstBeforePause = manager.findFirstCompletelyVisibleItemPosition()
            lastBeforePause = manager.findLastCompletelyVisibleItemPosition()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
        FeederApplication.bus.unregister(this)
    }



    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun syncStatus(status: SyncTask.SyncStatus) {
        if (status.isComplete) {
            setViewType(viewType, binding?.swipeRefreshLayout?.isRefreshing ?: false)
            binding?.swipeRefreshLayout?.isRefreshing = false
            displaySyncTime()
            binding?.toolbar?.menu?.findItem(R.id.menu_refresh)?.isEnabled = true
        } else {
            binding?.toolbar?.subtitle = status.status
        }
        if (status.exception != null && isResumed) {
            AlertDialog.Builder(activity)
                    .setMessage(status.exception!!.localizedMessage)
                    .setPositiveButton("OK") { dialog, _ -> dialog.dismiss() }
                    .show()
        }
    }

    private fun displaySyncTime() {
        val state = SyncState.latest(realm)
        val format = DateFormat.getDateTimeInstance()
        binding?.toolbar?.subtitle = "Last synced " + if (state == null) "never" else format.format(state.timeStamp)
    }


    fun childDisplayedEntryId(entryId: Int) {
        Timber.i("childDiplayedEntryId " + entryId)
        val entry = Entry.byId(realm, entryId).findFirst()
        if (entry != null && entry.unread && context != null) {
            Entry.setUnread(context, realm, entry, false)
        }
    }

    private fun setViewType(viewType: Entry.ViewType, clearState: Boolean) {
        // If we're keeping the same viewtype, then never _remove_ entries
        val currentIds = if (this.viewType == viewType && !clearState) entries?.map{ it.id } else null

        this.viewType = viewType
        binding?.viewType = this.viewType

        entries?.removeAllChangeListeners()
        entries = entries(realm, this.viewType, currentIds)
        entries?.addChangeListener({ results, _ ->
            adapter?.entries = results
            if (results.size == 0) {
                binding?.empty?.visibility = View.VISIBLE
            } else {
                binding?.empty?.visibility = View.GONE
            }
        })
    }

    fun entries(realm: Realm, viewType: Entry.ViewType, currently: List<Int>?): RealmResults<Entry> {
        // TODO bug, the first fime we build this it' sa raw query. So if we mark something as
        // read before we rebuild the list for any reason, that entry will dissapear.
        Timber.i("currently is $currently")
        val entries: RealmQuery<Entry> = when (viewType) {
            Entry.ViewType.UNREAD ->
                realm.where(Entry::class.java).equalTo("unread", true)
            Entry.ViewType.STARRED ->
                realm.where(Entry::class.java).equalTo("starred", true)
            Entry.ViewType.ALL ->
                realm.where(Entry::class.java)
        }
        currently?.forEach {
            entries.or().equalTo("id", it)
        }
        return entries.findAllSortedAsync("published", io.realm.Sort.DESCENDING)
    }

    companion object {
        private val VIEW_TYPE = "view_type"
    }


}