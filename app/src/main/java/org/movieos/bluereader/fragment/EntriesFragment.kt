package org.movieos.bluereader.fragment

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.support.v4.app.FragmentTransaction.TRANSIT_FRAGMENT_CLOSE
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.realm.Realm
import io.realm.RealmQuery
import io.realm.RealmResults
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.movieos.bluereader.MainActivity
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.R
import org.movieos.bluereader.databinding.EntriesFragmentBinding
import org.movieos.bluereader.databinding.EntryRowBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.model.SyncState
import org.movieos.bluereader.model.Tagging
import org.movieos.bluereader.utilities.BindingAdapter
import org.movieos.bluereader.utilities.Settings
import org.movieos.bluereader.utilities.SyncTask
import timber.log.Timber
import java.text.DateFormat


val BUNDLE_CURRENT_IDS: String = "bundle_current_ids"

class EntriesFragment : DataBindingFragment<EntriesFragmentBinding>() {

    internal val adapter = BindingAdapter()
    private val realm = Realm.getDefaultInstance()
    internal var viewType = Entry.ViewType.UNREAD
    internal val entries: RealmResults<Entry>
    internal val taggings: RealmResults<Tagging>
    internal val currentIds: MutableSet<Int> = mutableSetOf()

    init {
        entries = realm.where(Entry::class.java).findAllSortedAsync("published", io.realm.Sort.DESCENDING)
        entries.addChangeListener { entries, changeset ->
            render()
        }

        taggings = realm.where(Tagging::class.java).findAllSortedAsync("name", io.realm.Sort.DESCENDING)
        taggings.addChangeListener { taggings, changeset ->
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainApplication.bus.register(this)
        if (savedInstanceState != null) {
            val viewType = savedInstanceState.getSerializable(VIEW_TYPE) as Entry.ViewType?
            this.viewType = viewType ?: this.viewType
            currentIds.addAll(savedInstanceState.getIntegerArrayList(BUNDLE_CURRENT_IDS));
        }

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(VIEW_TYPE, viewType)
        outState.putIntegerArrayList(BUNDLE_CURRENT_IDS, ArrayList(currentIds))
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): EntriesFragmentBinding {
        val binding = EntriesFragmentBinding.inflate(inflater, container, false)
        binding.recyclerView.adapter = adapter
        //binding.recyclerView.itemAnimator = null

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

        binding.bottomNavigation.selectedItemId = when(viewType) {
            Entry.ViewType.UNREAD -> R.id.menu_unread
            Entry.ViewType.STARRED -> R.id.menu_starred
            Entry.ViewType.ALL -> R.id.menu_unread
            Entry.ViewType.FEEDS -> R.id.menu_feeds
        }

        binding.bottomNavigation.setOnNavigationItemSelectedListener {
            when (it.itemId) {
                R.id.menu_unread -> setViewType(Entry.ViewType.UNREAD)
                R.id.menu_starred -> setViewType(Entry.ViewType.STARRED)
                R.id.menu_all -> setViewType(Entry.ViewType.ALL)
                R.id.menu_feeds -> setViewType(Entry.ViewType.FEEDS)
            }
            binding.recyclerView.scrollToPosition(0)
            true // mark as selected
        }

        binding.bottomNavigation.setOnNavigationItemReselectedListener {
            currentIds.clear()
            render()
            binding.recyclerView.postDelayed({
                binding.recyclerView.smoothScrollToPosition(0)
            }, 1)
        }

        if (SyncState.latest(realm) == null) {
            // first run / first sync
            SyncTask.sync(activity, true, false)
        }

        return binding
    }

    override fun onResume() {
        super.onResume()
        displaySyncTime()
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        entries.removeAllChangeListeners()
        taggings.removeAllChangeListeners()
        realm.close()
        MainApplication.bus.unregister(this)
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun syncStatus(status: SyncTask.SyncStatus) {
        if (status.isComplete) {
            if (binding?.swipeRefreshLayout?.isRefreshing ?: false) {
                //currentIds.clear()
            }
            binding?.swipeRefreshLayout?.isRefreshing = false
            displaySyncTime()
            render()
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

    private fun setViewType(newViewType: Entry.ViewType) {
        Timber.i("setting new view type $newViewType")
        viewType = newViewType
        currentIds.clear()
        render()
    }

    private fun render() {
        Timber.i("currentIds are $currentIds")
        val builder = BindingAdapter.Builder()
        // currentIds is a list of things we're _currently_ showing. We only ever add items
        // to the visible list, except when we change view types, so that we return to a list
        // from the detail view that looks the same even though the dataset got regenerated.
        val visibleEntries = entries.filter {
            it.id in currentIds || when (viewType) {
                Entry.ViewType.FEEDS -> false
                Entry.ViewType.ALL -> true
                Entry.ViewType.UNREAD -> it.unread
                Entry.ViewType.STARRED -> it.starred
            }
        }
        val entryIds = visibleEntries.map {it.id}
        for (entry in visibleEntries) {
            currentIds += entry.id
            builder.addRow(EntryRowBinding::class.java, entry.id, { binding, view ->
                binding.entry = entry
                view.setOnClickListener {
                    Entry.setUnread(context, realm, entry, false)
                    val fragment = DetailFragment.create(entryIds, entry.id)
                    fragment.setTargetFragment(this@EntriesFragment, 0)
                    fragmentManager
                            .beginTransaction()
                            .setTransition(TRANSIT_FRAGMENT_CLOSE)
                            .replace(R.id.main_content, fragment)
                            .addToBackStack(null)
                            .commit()
                }

                binding.star.setOnClickListener { v ->
                    val newState = !v.isSelected
                    Entry.setStarred(context, realm, entry, newState)
                    v.isSelected = newState
                }
            })
        }
        adapter.fromBuilder(builder)

        if (visibleEntries.isEmpty()) {
            binding?.empty?.visibility = View.VISIBLE
        } else {
            binding?.empty?.visibility = View.GONE
        }
    }

    fun entries(realm: Realm, viewType: Entry.ViewType, currently: List<Int>?): RealmResults<Entry> {
        Timber.i("currently is $currently")
        val entries: RealmQuery<Entry> = when (viewType) {
            Entry.ViewType.UNREAD ->
                realm.where(Entry::class.java).equalTo("unread", true)
            Entry.ViewType.STARRED ->
                realm.where(Entry::class.java).equalTo("starred", true)
            Entry.ViewType.ALL ->
                realm.where(Entry::class.java)
            Entry.ViewType.FEEDS ->
                realm.where(Entry::class.java)
        }
        if (currently == null) {
            return entries(realm, viewType, entries.findAll().map{it.id})
        }

        currently.forEach {
            entries.or().equalTo("id", it)
        }
        return entries.findAllSortedAsync("published", io.realm.Sort.DESCENDING)
    }

    companion object {
        private val VIEW_TYPE = "view_type"
    }


}