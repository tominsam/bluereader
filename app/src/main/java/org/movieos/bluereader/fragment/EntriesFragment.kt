package org.movieos.bluereader.fragment

import android.app.AlertDialog
import android.arch.lifecycle.LiveData
import android.arch.lifecycle.Observer
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.transition.TransitionInflater
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import org.movieos.bluereader.MainActivity
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.R
import org.movieos.bluereader.dao.MainDatabase
import org.movieos.bluereader.databinding.EntriesFragmentBinding
import org.movieos.bluereader.databinding.FeedRowBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.model.Subscription
import org.movieos.bluereader.model.SyncState
import org.movieos.bluereader.utilities.*
import timber.log.Timber
import java.text.DateFormat

private const val BUNDLE_CURRENT_IDS: String = "bundle_current_ids"
private const val BUNDLE_VIEW_TYPE = "view_type"
private const val BUNDLE_FITER_NAME = "filter_name"
private const val BUNDLE_FITER_FEED = "filter_feed"
private const val BUNDLE_EXPANDED_TAGGINGS = "expanded_taggings"

class EntriesFragment : DataBindingFragment<EntriesFragmentBinding>() {

    // Transient fragment state

    // Watches for changes to app sync state
    var syncState: LiveData<SyncState>? = null

    // Adapter that shows entries efficiently. Keep this on the fragment so that
    // we can switch view types without scrolling to the top because we created
    // a new adapter
    val entriesAdapter: EntriesAdapter

    // Adapter for displaying feeds/tags efficiently.
    val feedsAdapter = BindingAdapter()

    // Persisted view states:

    // A list of entry IDs we're looking at, so we don't ever remove things
    val currentIds: MutableSet<Int> = mutableSetOf()
    // Display name for the current feed filter
    var filterName: String? = null
    // List of feed IDs we're filtering to, empty for "show all"
    var filterFeed: Collection<Int> = emptyList()
    // current selected view type
    var viewType = Entry.ViewType.UNREAD
    // Track which feed tags are expanded
    var expandedTaggings: MutableSet<String> = mutableSetOf()

    val database: MainDatabase
        get() = (activity.application as MainApplication).database

    init {
        entriesAdapter = EntriesAdapter({ entryIds, index ->
            val fragment = DetailFragment.create(entryIds, index)
            fragment.setTargetFragment(this, 0)
            fragment.enterTransition = TransitionInflater.from(context).inflateTransition(R.transition.detail_enter)
            fragment.returnTransition = TransitionInflater.from(context).inflateTransition(R.transition.detail_enter)
            fragmentManager
                    .beginTransaction()
                    .add(R.id.main_content, fragment)
                    .addToBackStack(null)
                    .commit()
        }, { entry, newState ->
            database.entryDao().setStarred(entry.id, newState)
            SyncTask.pushSoon(activity)
            newState
        })
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        MainApplication.bus.register(this)
        if (savedInstanceState != null) {
            val viewType = savedInstanceState.getSerializable(BUNDLE_VIEW_TYPE) as Entry.ViewType?
            this.viewType = viewType ?: this.viewType
            currentIds.addAll(savedInstanceState.getIntegerArrayList(BUNDLE_CURRENT_IDS))
            filterName = savedInstanceState.getString(BUNDLE_FITER_NAME)
            filterFeed = savedInstanceState.getIntegerArrayList(BUNDLE_FITER_FEED) ?: emptyList()
            expandedTaggings = savedInstanceState.getStringArrayList(BUNDLE_EXPANDED_TAGGINGS).toHashSet()
        }
        syncState = database.entryDao().watchSyncState()
        syncState?.observe(this, Observer { state ->
            displaySyncTime(state)
        })

    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable(BUNDLE_VIEW_TYPE, viewType)
        outState.putIntegerArrayList(BUNDLE_CURRENT_IDS, ArrayList(currentIds))
        outState.putString(BUNDLE_FITER_NAME, filterName)
        outState.putIntegerArrayList(BUNDLE_FITER_FEED, ArrayList(filterFeed))
        outState.putStringArrayList(BUNDLE_EXPANDED_TAGGINGS, ArrayList(expandedTaggings))
    }


    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): EntriesFragmentBinding {
        val binding = EntriesFragmentBinding.inflate(inflater, container, false)
        //binding.recyclerView.itemAnimator = null

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
                    database.entryDao().wipeSubscriptions()
                    database.entryDao().wipeEntries()
                    database.entryDao().wipeTaggings()
                    database.entryDao().wipeLocalState()
                    database.entryDao().wipeSyncState()
                    startActivity(Intent(activity, MainActivity::class.java))
                }
            }
            true
        }

        binding.swipeRefreshLayout.setOnRefreshListener {
            binding.toolbar.menu?.findItem(R.id.menu_refresh)?.isEnabled = false
            SyncTask.sync(activity, true, false)
        }

        binding.navigationFeeds.setOnClickListener { changeViewType(Entry.ViewType.FEEDS) }
        binding.navigationAll.setOnClickListener { changeViewType(Entry.ViewType.ALL) }
        binding.navigationUnread.setOnClickListener { changeViewType(Entry.ViewType.UNREAD) }
        binding.navigationStarred.setOnClickListener { changeViewType(Entry.ViewType.STARRED) }

        return binding
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    override fun onDestroy() {
        super.onDestroy()
        MainApplication.bus.unregister(this)
    }

    fun onBackPressed(): Boolean {
        if (viewType != Entry.ViewType.FEEDS && !filterFeed.isEmpty()) {
            changeViewType(Entry.ViewType.FEEDS)
            return true
        }
        return false
    }

    @Suppress("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    fun syncStatus(status: SyncTask.SyncStatus) {
        Timber.i("seen status $status")
        if (status.isComplete || TextUtils.isEmpty(status.status)) {
            if (binding?.swipeRefreshLayout?.isRefreshing ?: false) {
                currentIds.clear()
            }
            binding?.swipeRefreshLayout?.isRefreshing = false
            binding?.toolbar?.menu?.findItem(R.id.menu_refresh)?.isEnabled = true
            if (currentIds.isEmpty() || isResumed) {
                // TODO isResumed isn;t what I want, I suspect
                render()
            }
            displaySyncTime(database.entryDao().syncState())
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

    private fun displaySyncTime(state: SyncState?) {
        val format = DateFormat.getDateTimeInstance()
        binding?.toolbar?.subtitle = "Last synced " + if (state == null) "never" else format.format(state.timeStamp)
    }


    fun childDisplayedEntryId(entryId: Int) {
        Timber.i("childDiplayedEntryId " + entryId)
        database.entryDao().setUnread(entryId, false)
        childChangedEntryState()
    }

    fun childChangedEntryState() {
        binding?.recyclerView?.adapter?.notifyDataSetChanged()
        SyncTask.pushSoon(activity)
    }

    private fun changeViewType(newViewType: Entry.ViewType) {
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
        Timber.i("Render")
        binding?.toolbar?.title = filterName ?: getString(R.string.all_entries)

        binding?.navigationUnread?.isSelected = viewType == Entry.ViewType.UNREAD
        binding?.navigationStarred?.isSelected = viewType == Entry.ViewType.STARRED
        binding?.navigationAll?.isSelected = viewType == Entry.ViewType.ALL
        binding?.navigationFeeds?.isSelected = viewType == Entry.ViewType.FEEDS

        if (viewType == Entry.ViewType.FEEDS) {
            val builder = BindingAdapter.Builder()
            buildFeeds(builder)
            feedsAdapter.fromBuilder(builder)
            if (binding?.recyclerView?.adapter != feedsAdapter) {
                binding?.recyclerView?.adapter = feedsAdapter
            }
        } else {
            entriesAdapter.rows = buildEntries()
            if (binding?.recyclerView?.adapter != entriesAdapter) {
                binding?.recyclerView?.adapter = entriesAdapter
            }
        }

        if (binding?.recyclerView?.adapter?.itemCount == 0) {
            binding?.empty?.visibility = View.VISIBLE
        } else {
            binding?.empty?.visibility = View.GONE
        }

        displaySyncTime(syncState?.value)
    }

    private fun buildEntries(): List<Int> {
        // currentIds is a list of things we're _currently_ showing. We only ever add items
        // to the visible list, except when we change view types, so that we return to a list
        // from the detail view that looks the same even though the dataset got regenerated.

        // feedIds needs to be a list of all feed IDs if the filter is empty
        val feedIds: Array<Int> = if (filterFeed.isEmpty()) database.entryDao().subscriptionFeedIds() else filterFeed.toTypedArray()

        val entries = measureTimeMillis("entries") {
            when (viewType) {
                Entry.ViewType.UNREAD -> database.entryDao().unreadVisible(feedIds)
                Entry.ViewType.STARRED -> database.entryDao().starredVisible(feedIds)
                Entry.ViewType.ALL -> database.entryDao().allVisible(feedIds)
                else -> throw RuntimeException("Can't happen ($viewType)")
            }
        }

        // Now we have a base list. If this is the first time we generated the list
        // (current IDs is empty) then turn the list into a list of current Ids, so that
        // we never remove things from the list. There's no point in doing this for the
        // all list and it's super expensive.
        if (currentIds.isEmpty() && viewType != Entry.ViewType.ALL && entries.isNotEmpty()) {
            currentIds.addAll(entries.map { it })
            //return buildEntries()
        }

        return entries
    }

    data class FeedRow(
            val name: String?,
            val subscriptions: List<Subscription>,
            val selected: Boolean) {
        val unread: Int
            get() = subscriptions.sumBy { it.unreadCount }
    }

    private fun buildFeeds(builder: BindingAdapter.Builder) {
        val allSubscriptions = database.entryDao().subscriptions()
        val untagged = allSubscriptions.toMutableList()

        // Group subs into tags
        val taggedSubscriptions: MutableMap<String, FeedRow> = mutableMapOf()
        for (tagging in database.entryDao().taggings()) {
            val name = tagging.name ?: continue
            val subscription = tagging.subscription ?: continue
            untagged.remove(subscription)

            if (taggedSubscriptions[name] == null) {
                taggedSubscriptions[name] = FeedRow(name, selected = filterName == name, subscriptions = mutableListOf())
            }
            (taggedSubscriptions[name]!!.subscriptions as MutableList) += subscription
        }

        // Add "all entries" row
        addFeedrow(builder, -1, FeedRow(null, selected = filterName == null, subscriptions = allSubscriptions))
        // Add a row per tag
        taggedSubscriptions.values.sortedBy { it.name }.forEachIndexed { index, feedRow ->
            addFeedrow(builder, index, feedRow)
        }
        if (untagged.isNotEmpty()) {
            addFeedrow(builder, -2, FeedRow("Untagged", selected = false, subscriptions = untagged))
        }

    }

    private fun addFeedrow(builder: BindingAdapter.Builder, index: Int, feedRow: FeedRow) {
        val expanded = (feedRow.name ?: "") in expandedTaggings

        builder.addRow(FeedRowBinding::class.java, index) { rowBinding, view ->
            rowBinding.feedRow = feedRow
            rowBinding.expand.visibility = View.VISIBLE
            rowBinding.expand.rotation = if (expanded) 90f else 0f
            view.setOnClickListener {
                filterName = feedRow.name
                filterFeed = if (feedRow.name == null) emptyList() else feedRow.subscriptions.map { it.feedId }
                changeViewType(Entry.ViewType.UNREAD)
            }
            rowBinding.expand.setOnClickListener {
                if (expanded) {
                    expandedTaggings.remove(feedRow.name ?: "")
                } else {
                    expandedTaggings.add(feedRow.name ?: "")
                }
                render()
            }
        }
        if (expanded) {
            for (subscription in feedRow.subscriptions.sortedBy { it.title }) {
                builder.addRow(FeedRowBinding::class.java, index * 100000 + subscription.id) { rowBinding, view ->
                    rowBinding.feedRow = FeedRow(subscription.title ?: "", selected = filterFeed == listOf(subscription.feedId), subscriptions = listOf(subscription))
                    rowBinding.expand.visibility = View.INVISIBLE
                    rowBinding.expand.setOnClickListener(null)
                    view.setOnClickListener {
                        filterName = subscription.title
                        filterFeed = listOf(subscription.feedId)
                        changeViewType(Entry.ViewType.UNREAD)
                    }
                }
            }
        }
    }

}