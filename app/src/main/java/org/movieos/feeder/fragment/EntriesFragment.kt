package org.movieos.feeder.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.support.v4.app.FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.realm.Realm
import io.realm.RealmResults
import io.realm.Sort
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.movieos.feeder.FeederApplication
import org.movieos.feeder.R
import org.movieos.feeder.databinding.EntriesFragmentBinding
import org.movieos.feeder.databinding.EntryRowBinding
import org.movieos.feeder.model.Entry
import org.movieos.feeder.model.SyncState
import org.movieos.feeder.utilities.RealmAdapter
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

        setViewType(viewType)
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putSerializable(VIEW_TYPE, viewType)
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
            }
            true
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.toolbar.menu?.findItem(R.id.menu_refresh)?.isEnabled = false
            SyncTask.sync(activity, true, false)
        }

        binding.stateUnread.setOnClickListener { setViewType(Entry.ViewType.UNREAD) }
        binding.stateStarred.setOnClickListener { setViewType(Entry.ViewType.STARRED) }
        binding.stateAll.setOnClickListener { setViewType(Entry.ViewType.ALL) }

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
            binding?.swipeRefreshLayout?.isRefreshing = false
            setViewType(viewType)
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
        val entry = Entry.byId(realm, entryId)
        if (entry != null && entry.unread && context != null) {
            Entry.setUnread(context, realm, entry, false)
        }
    }

    private fun setViewType(viewType: Entry.ViewType) {
        this.viewType = viewType
        binding?.viewType = this.viewType

        entries?.removeAllChangeListeners()
        entries = entries(realm, this.viewType)
        entries?.addChangeListener({ results, changeset ->
            adapter?.entries = results
            if (results.size == 0) {
                binding?.empty?.visibility = View.VISIBLE
            } else {
                binding?.empty?.visibility = View.GONE
            }
        })
    }

    fun entries(realm: Realm, viewType: Entry.ViewType): RealmResults<Entry> {
        val entries = when (viewType) {
            Entry.ViewType.UNREAD ->
                realm.where(Entry::class.java).equalTo("unread", true).findAllSortedAsync("published", Sort.DESCENDING)
            Entry.ViewType.STARRED ->
                realm.where(Entry::class.java).equalTo("starred", true).findAllSortedAsync("published", Sort.DESCENDING)
            Entry.ViewType.ALL ->
                realm.where(Entry::class.java).findAllSortedAsync("published", Sort.DESCENDING)
        }
//        if (entries.size == 0) {
//            // gratuitous impossible data set
//            return realm.where(Entry::class.java).equalTo("id", -1).findAllAsync()
//        }
        return entries;
//        // So this looks weird. We get the matching entries, but the _real_ reusltset is pinned
//        // to "things that matched the query when we ran it", so that pushing read state to the
//        // server won't remove elements from the list. Force-refreshing the entries list will
//        // rebuild the query and show more things.
//        val ids = (entries.map { it.id }).sliceSafely(0, 2000).toTypedArray()
//        Timber.i("ids are ${Arrays.toString(ids)}")
//
//        return realm.where(Entry::class.java).`in`("id", ids).findAllSortedAsync("published", Sort.DESCENDING)
    }


    companion object {

        private val VIEW_TYPE = "view_type"
    }


}