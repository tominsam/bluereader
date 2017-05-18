package org.movieos.feeder.fragment

import android.app.AlertDialog
import android.os.Bundle
import android.support.v7.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.ViewGroup
import io.realm.Realm
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
    private var realm: Realm? = null
    private var firstBeforePause: Int = 0
    private var lastBeforePause: Int = 0

    internal var viewType: Entry.ViewType = Entry.ViewType.UNREAD

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FeederApplication.bus.register(this)
        realm = Realm.getDefaultInstance()
        if (savedInstanceState != null) {
            val viewType = savedInstanceState.getSerializable(VIEW_TYPE) as Entry.ViewType?
            if (viewType != null) {
                this.viewType = viewType
            }
        }

        val entries = Entry.entries(realm!!, viewType)

        adapter = object : RealmAdapter<Entry, EntryRowBinding>(EntryRowBinding::class.java, entries) {
            override fun onBindViewHolder(holder: RealmAdapter.FeedViewHolder<EntryRowBinding>, instance: Entry) {
                holder.binding.entry = instance
                holder.itemView.setOnClickListener { v ->
                    Entry.setUnread(context, realm!!, Entry.byId(instance.id)!!, false)
                    val fragment = DetailFragment.create(ids, instance.id)
                    fragment.setTargetFragment(this@EntriesFragment, 0)
                    fragmentManager
                            .beginTransaction()
                            .replace(R.id.main_content, fragment)
                            .addToBackStack(null)
                            .commit()
                }
                holder.binding.star.setOnClickListener { v ->
                    val newState = !v.isSelected
                    Entry.setStarred(context, realm!!, instance, newState)
                    v.isSelected = newState
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle?) {
        super.onSaveInstanceState(outState)
        outState!!.putSerializable(VIEW_TYPE, viewType)
    }

    override fun onDestroy() {
        super.onDestroy()
        realm!!.close()
        FeederApplication.bus.unregister(this)
    }


    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): EntriesFragmentBinding {
        val binding = EntriesFragmentBinding.inflate(inflater, container, false)
        binding.viewType = viewType
        binding.recyclerView.adapter = adapter
        binding.toolbar.inflateMenu(R.menu.entries_menu)
        binding.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_refresh -> {
                    SyncTask.sync(activity, true, false)
                }
            }
            true
        }

        binding.stateUnread.setOnClickListener { v -> setViewType(Entry.ViewType.UNREAD) }
        binding.stateStarred.setOnClickListener { v -> setViewType(Entry.ViewType.STARRED) }
        binding.stateAll.setOnClickListener { v -> setViewType(Entry.ViewType.ALL) }

        return binding
    }

    override fun onResume() {
        super.onResume()
        displaySyncTime()

        // if we changed page in the detail view, scroll to minimally make that view visible.
        // To do this we tracked the first and last visible rows before we left (because in this
        // method we're not laid out yet), and will assume this has not changed. If the phone
        // has rotated or resized we'll guess wrong here.
        if (binding != null && currentEntry >= 0 && firstBeforePause >= 0 && lastBeforePause >= 0) {
            if (currentEntry < firstBeforePause) {
                binding!!.recyclerView.scrollToPosition(currentEntry)
            } else if (currentEntry > lastBeforePause) {
                binding!!.recyclerView.scrollToPosition(currentEntry - (lastBeforePause - firstBeforePause))
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

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun syncStatus(status: SyncTask.SyncStatus) {
        if (binding == null) {
            return
        }
        if (status.isComplete) {
            displaySyncTime()
        } else {
            binding!!.toolbar.subtitle = status.status
        }
        if (status.exception != null && isResumed) {
            AlertDialog.Builder(activity)
                    .setMessage(status.exception!!.localizedMessage)
                    .setPositiveButton("OK") { dialog, which -> dialog.dismiss() }
                    .show()
        }
    }

    private fun displaySyncTime() {
        val state = SyncState.latest(realm!!)
        val format = DateFormat.getDateTimeInstance()
        binding?.toolbar?.subtitle = "Last synced " + if (state == null) "never" else format.format(state.timeStamp)
    }


    fun childDisplayedEntryId(entryId: Int) {
        Timber.i("childDiplayedEntryId " + entryId)
        val entry = Entry.byId(entryId)
        if (entry != null && entry.isLocallyUnread) {
            Entry.setUnread(context, realm!!, entry, false)
        }
        //        if (mBinding != null) {
        //            mBinding.recyclerView.scrollToPosition();
        //        } else {
        //            mCurrentEntry = position;
        //        }
    }

    private fun setViewType(viewType: Entry.ViewType) {
        this.viewType = viewType
        binding?.viewType = this.viewType
        adapter?.setQuery(Entry.entries(realm!!, this.viewType))
    }

    companion object {

        private val VIEW_TYPE = "view_type"
    }


}