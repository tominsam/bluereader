package org.movieos.bluereader.fragment

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.realm.Realm
import io.realm.RealmResults
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.movieos.bluereader.MainActivity
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.R
import org.movieos.bluereader.databinding.EntriesFragmentBinding
import org.movieos.bluereader.databinding.EntryRowBinding
import org.movieos.bluereader.databinding.FeedRowBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.model.Subscription
import org.movieos.bluereader.model.SyncState
import org.movieos.bluereader.model.Tagging
import org.movieos.bluereader.utilities.BindingAdapter
import org.movieos.bluereader.utilities.Settings
import org.movieos.bluereader.utilities.SyncTask
import timber.log.Timber
import java.text.DateFormat


private val BUNDLE_CURRENT_IDS: String = "bundle_current_ids"
private val BUNDLE_VIEW_TYPE = "view_type"
private val BUNDLE_FITER_NAME = "filter_name"
private val BUNDLE_FITER_FEED = "filter_feed"

class EntriesFragment : DataBindingFragment<EntriesFragmentBinding>() {

    internal val adapter = BindingAdapter()
    private val realm = Realm.getDefaultInstance()
    internal var viewType = Entry.ViewType.UNREAD
    internal val entries: RealmResults<Entry>
    internal val taggings: RealmResults<Tagging>
    internal val currentIds: MutableSet<Int> = mutableSetOf()
    internal val expandedTaggings: MutableSet<String> = mutableSetOf()
    internal var filterName: String? = null
    internal var filterFeed: Int? = null

    init {
        entries = realm.where(Entry::class.java).findAllSortedAsync("published", io.realm.Sort.DESCENDING)
        entries.addChangeListener { _, _ ->
            render()
        }

        taggings = realm.where(Tagging::class.java).findAllAsync()
        taggings.addChangeListener { _, _ ->
            render()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MainApplication.bus.register(this)
        if (savedInstanceState != null) {
            val viewType = savedInstanceState.getSerializable(BUNDLE_VIEW_TYPE) as Entry.ViewType?
            this.viewType = viewType ?: this.viewType
            currentIds.addAll(savedInstanceState.getIntegerArrayList(BUNDLE_CURRENT_IDS))
            filterName = savedInstanceState.getString(BUNDLE_FITER_NAME)
            filterFeed = savedInstanceState.getInt(BUNDLE_FITER_FEED)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(BUNDLE_VIEW_TYPE, viewType)
        outState.putIntegerArrayList(BUNDLE_CURRENT_IDS, ArrayList(currentIds))
        outState.putString(BUNDLE_FITER_NAME, filterName)
        if (filterFeed != null) {
            outState.putInt(BUNDLE_FITER_FEED, filterFeed!!)
        }
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): EntriesFragmentBinding {
        val binding = EntriesFragmentBinding.inflate(inflater, container, false)
        binding.recyclerView.adapter = adapter

        binding.toolbar.inflateMenu(R.menu.entries_menu)
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_refresh -> {
                    item.isEnabled = false
                    SyncTask.sync(activity, true, false)
                }
                R.id.menu_theme -> {
                    activity.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                            .putBoolean("night_mode", !(activity as MainActivity).wantNightMode())
                            .apply()
                    activity.recreate()
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

        binding.navigationFeeds.setOnClickListener { setViewType(Entry.ViewType.FEEDS) }
        binding.navigationAll.setOnClickListener { setViewType(Entry.ViewType.ALL) }
        binding.navigationUnread.setOnClickListener { setViewType(Entry.ViewType.UNREAD) }
        binding.navigationStarred.setOnClickListener { setViewType(Entry.ViewType.STARRED) }

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

    fun onBackPressed(): Boolean {
        if (viewType != Entry.ViewType.FEEDS && binding != null) {
            setViewType(Entry.ViewType.FEEDS)
            return true
        }
        return false
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun syncStatus(status: SyncTask.SyncStatus) {
        if (status.isComplete) {
            if (binding?.swipeRefreshLayout?.isRefreshing ?: false) {
                currentIds.clear()
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
        viewType = newViewType
        currentIds.clear()
        render()
        if (viewType == newViewType) {
            Timber.i("resetting existing view type $newViewType")
            binding?.recyclerView?.postDelayed({
                binding?.recyclerView?.smoothScrollToPosition(0)
            }, 1)
        } else {
            Timber.i("setting new view type $newViewType")
            binding?.recyclerView?.scrollToPosition(0)
        }
    }

    private fun render() {
        Timber.i("currentIds are $currentIds")
        binding?.toolbar?.title = filterName ?: getString(R.string.all_entries)

        binding?.navigationUnread?.isSelected = viewType == Entry.ViewType.UNREAD
        binding?.navigationStarred?.isSelected = viewType == Entry.ViewType.STARRED
        binding?.navigationAll?.isSelected = viewType == Entry.ViewType.ALL
        binding?.navigationFeeds?.isSelected = viewType == Entry.ViewType.FEEDS

        val builder = BindingAdapter.Builder()
        if (viewType == Entry.ViewType.FEEDS) {
            buildFeeds(builder)
        } else {
            buildEntries(builder)
        }
        adapter.fromBuilder(builder)

        if (adapter.itemCount == 0) {
            binding?.empty?.visibility = View.VISIBLE
        } else {
            binding?.empty?.visibility = View.GONE
        }

    }

    private fun buildEntries(builder: BindingAdapter.Builder) {
        // currentIds is a list of things we're _currently_ showing. We only ever add items
        // to the visible list, except when we change view types, so that we return to a list
        // from the detail view that looks the same even though the dataset got regenerated.

        val filterSubscriptionsList = if (filterFeed == null) {
            taggings.filter { it.name == filterName }.map { it.feedId }
        } else {
            listOf(filterFeed!!)
        }

        val visibleEntries = entries.filter {
            val isFiltered = filterSubscriptionsList.isEmpty() || it.feedId in filterSubscriptionsList
            val isCurrent = it.id in currentIds
            val isType = when (viewType) {
                Entry.ViewType.FEEDS -> false
                Entry.ViewType.ALL -> true
                Entry.ViewType.UNREAD -> it.unread
                Entry.ViewType.STARRED -> it.starred
            }
            isFiltered && (isCurrent || isType)
        }

        val entryIds = visibleEntries.map { it.id }
        for (entry in visibleEntries) {
            currentIds += entry.id
            builder.addRow(EntryRowBinding::class.java, entry.id, { binding, view ->
                binding.entry = entry
                view.setOnClickListener {
                    Entry.setUnread(context, realm, entry, false)
                    val fragment = DetailFragment.create(entryIds, entry.id)
                    fragment.setTargetFragment(this@EntriesFragment, 0)
                    fragment.enterTransition = TransitionInflater.from(context).inflateTransition(R.transition.detail_enter)
                    fragment.returnTransition = TransitionInflater.from(context).inflateTransition(R.transition.detail_enter)
                    fragmentManager
                            .beginTransaction()
                            .add(R.id.main_content, fragment)
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
    }

    data class FeedRow(
            val name: String,
            val subscriptions: MutableList<Subscription> = mutableListOf(),
            var unread: Int = 0,
            val selected: Boolean)

    private fun buildFeeds(builder: BindingAdapter.Builder) {

        // Build fast map of subscription feedId -> unread count
        val unreadCounts: MutableMap<Int, Int> = mutableMapOf()
        var totalUnread: Int = 0
        val allSubscriptions: MutableSet<Subscription> = mutableSetOf()

        entries.forEach {
            if (it.subscription != null) {
                allSubscriptions += it.subscription!!
            }
            if (it.unread) {
                unreadCounts[it.feedId] = (unreadCounts[it.feedId] ?: 0) + 1
                totalUnread += 1
            }
        }

        // Group subs into tags
        val taggedSubscriptions: MutableMap<String, FeedRow> = mutableMapOf()
        for (tagging in taggings) {
            val name = tagging.name ?: continue
            val subscription = tagging.subscription ?: continue

            if (taggedSubscriptions[name] == null) {
                taggedSubscriptions[name] = FeedRow(name, selected = filterName == name && filterFeed == null)
            }
            taggedSubscriptions[name]!!.subscriptions += subscription
            taggedSubscriptions[name]!!.unread += unreadCounts[subscription.feedId] ?: 0
        }

        // Add "all entries" row
        addFeedrow(builder, -1, FeedRow(getString(R.string.all_entries), unread = totalUnread, selected = filterName == null && filterFeed == null), unreadCounts, allSubscriptions)
        // Add a row per tag
        taggedSubscriptions.values.sortedBy { it.name }.forEachIndexed { index, feedRow ->
            addFeedrow(builder, index, feedRow, unreadCounts, feedRow.subscriptions)
        }

    }

    private fun addFeedrow(builder: BindingAdapter.Builder, index: Int, feedRow: FeedRow, unreadCounts: MutableMap<Int, Int>, subscriptions: Collection<Subscription>) {
        val expanded = feedRow.name in expandedTaggings

        builder.addRow(FeedRowBinding::class.java, index) { rowBinding, view ->
            rowBinding.feedRow = feedRow
            rowBinding.expand.visibility = View.VISIBLE
            rowBinding.expand.rotation = if (expanded) 90f else 0f
            view.setOnClickListener {
                filterName = feedRow.name
                filterFeed = null
                setViewType(Entry.ViewType.UNREAD)
            }
            rowBinding.expand.setOnClickListener {
                if (expanded) {
                    expandedTaggings.remove(feedRow.name)
                } else {
                    expandedTaggings.add(feedRow.name)
                }
                render()
            }
        }
        if (expanded) {
            for (subscription in subscriptions.sortedBy { it.title }) {
                builder.addRow(FeedRowBinding::class.java, index * 100000 + subscription.id) { rowBinding, view ->
                    rowBinding.feedRow = FeedRow(subscription.title ?: "", selected = filterFeed == subscription.feedId, unread = unreadCounts[subscription.feedId] ?: 0)
                    rowBinding.expand.visibility = View.INVISIBLE
                    rowBinding.expand.setOnClickListener(null)
                    view.setOnClickListener {
                        filterName = subscription.title
                        filterFeed = subscription.feedId
                        setViewType(Entry.ViewType.UNREAD)
                    }
                }
            }
        }
    }

}