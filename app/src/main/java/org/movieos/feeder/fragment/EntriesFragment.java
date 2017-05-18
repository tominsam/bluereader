package org.movieos.feeder.fragment;

import android.app.AlertDialog;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;
import org.movieos.feeder.FeederApplication;
import org.movieos.feeder.R;
import org.movieos.feeder.databinding.EntriesFragmentBinding;
import org.movieos.feeder.databinding.EntryRowBinding;
import org.movieos.feeder.model.Entry;
import org.movieos.feeder.model.SyncState;
import org.movieos.feeder.utilities.RealmAdapter;
import org.movieos.feeder.utilities.SyncTask;

import java.text.DateFormat;

import io.realm.Realm;
import io.realm.RealmResults;
import timber.log.Timber;

public class EntriesFragment extends DataBindingFragment<EntriesFragmentBinding> {

    private static final String VIEW_TYPE = "view_type";

    RealmAdapter<Entry, EntryRowBinding> mAdapter;
    int mCurrentEntry = -1;
    private Realm mRealm;
    private int mFirstBeforePause;
    private int mLastBeforePause;

    @NonNull
    Entry.ViewType mViewType = Entry.ViewType.UNREAD;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FeederApplication.Companion.getBus().register(this);
        mRealm = Realm.getDefaultInstance();
        if (savedInstanceState != null) {
            mViewType = (Entry.ViewType) savedInstanceState.getSerializable(VIEW_TYPE);
            if (mViewType == null) {
                throw new AssertionError("null viewtype in saved instancestate");
            }
        }

        RealmResults<Entry> entries = Entry.Companion.entries(mRealm, mViewType);

        mAdapter = new RealmAdapter<Entry, EntryRowBinding>(EntryRowBinding.class, entries) {
            @Override
            public void onBindViewHolder(FeedViewHolder<EntryRowBinding> holder, Entry instance) {
                holder.getBinding().setEntry(instance);
                holder.itemView.setOnClickListener(v -> {
                    Entry.Companion.setUnread(getContext(), mRealm, Entry.Companion.byId(instance.getId()), false);
                    DetailFragment fragment = DetailFragment.create(getIds(), instance.getId());
                    fragment.setTargetFragment(EntriesFragment.this, 0);
                    getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_content, fragment)
                        .addToBackStack(null)
                        .commit();
                });
                holder.getBinding().star.setOnClickListener(v -> {
                    boolean newState = !v.isSelected();
                    Entry.Companion.setStarred(getContext(), mRealm, instance, newState);
                    v.setSelected(newState);
                });
            }
        };
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(VIEW_TYPE, mViewType);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
        FeederApplication.Companion.getBus().unregister(this);
    }


    @NonNull
    @Override
    protected EntriesFragmentBinding createBinding(@NotNull final LayoutInflater inflater, @org.jetbrains.annotations.Nullable final ViewGroup container) {
        EntriesFragmentBinding binding = EntriesFragmentBinding.inflate(inflater, container, false);
        binding.setViewType(mViewType);
        binding.recyclerView.setAdapter(mAdapter);
        binding.toolbar.inflateMenu(R.menu.entries_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.menu_refresh:
                    SyncTask.Companion.sync(getActivity(), true, false);
                    return true;
                default:
                    return false;
            }
        });

        binding.stateUnread.setOnClickListener(v -> setViewType(Entry.ViewType.UNREAD));
        binding.stateStarred.setOnClickListener(v -> setViewType(Entry.ViewType.STARRED));
        binding.stateAll.setOnClickListener(v -> setViewType(Entry.ViewType.ALL));

        return binding;
    }

    @Override
    public void onResume() {
        super.onResume();
        displaySyncTime();

        // if we changed page in the detail view, scroll to minimally make that view visible.
        // To do this we tracked the first and last visible rows before we left (because in this
        // method we're not laid out yet), and will assume this has not changed. If the phone
        // has rotated or resized we'll guess wrong here.
        if (getBinding() != null && mCurrentEntry >= 0 && mFirstBeforePause >= 0 && mLastBeforePause >= 0) {
            if (mCurrentEntry < mFirstBeforePause) {
                getBinding().recyclerView.scrollToPosition(mCurrentEntry);
            } else if (mCurrentEntry > mLastBeforePause) {
                getBinding().recyclerView.scrollToPosition(mCurrentEntry - (mLastBeforePause - mFirstBeforePause));
            }
            mCurrentEntry = -1;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getBinding() != null) {
            LinearLayoutManager manager = (LinearLayoutManager) getBinding().recyclerView.getLayoutManager();
            mFirstBeforePause = manager.findFirstCompletelyVisibleItemPosition();
            mLastBeforePause = manager.findLastCompletelyVisibleItemPosition();
        }
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void syncStatus(SyncTask.SyncStatus status) {
        if (getBinding() == null) {
            return;
        }
        if (status.isComplete()) {
            displaySyncTime();
        } else {
            getBinding().toolbar.setSubtitle(status.getStatus());
        }
        if (status.getException() != null && isResumed()) {
            new AlertDialog.Builder(getActivity())
                .setMessage(status.getException().getLocalizedMessage())
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .show();
        }
    }

    private void displaySyncTime() {
        if (getBinding() == null) {
            return;
        }
        SyncState state = SyncState.Companion.latest(mRealm);
        DateFormat format = DateFormat.getDateTimeInstance();
        getBinding().toolbar.setSubtitle("Last synced " + (state == null ? "never" : format.format(state.getTimeStamp())));
    }


    public void childDisplayedEntryId(int entryId) {
        Timber.i("childDiplayedEntryId " + entryId);
        Entry entry = Entry.Companion.byId(entryId);
        if (entry != null && entry.isLocallyUnread()) {
            Entry.Companion.setUnread(getContext(), mRealm, entry, false);
        }
//        if (mBinding != null) {
//            mBinding.recyclerView.scrollToPosition();
//        } else {
//            mCurrentEntry = position;
//        }
    }

    private void setViewType(@NonNull Entry.ViewType viewType) {
        mViewType = viewType;
        if (getBinding() != null) {
            getBinding().setViewType(mViewType);
        }
        mAdapter.setQuery(Entry.Companion.entries(mRealm, mViewType));
    }



}