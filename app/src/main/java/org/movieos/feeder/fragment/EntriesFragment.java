package org.movieos.feeder.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.widget.LinearLayoutManager;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.movieos.feeder.FeederApplication;
import org.movieos.feeder.R;
import org.movieos.feeder.databinding.EntriesFragmentBinding;
import org.movieos.feeder.databinding.EntryRowBinding;
import org.movieos.feeder.model.Entry;
import org.movieos.feeder.utilities.RealmAdapter;
import org.movieos.feeder.utilities.SyncTask;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class EntriesFragment extends DataBindingFragment<EntriesFragmentBinding> {

    RealmAdapter<Entry, EntryRowBinding> mAdapter;
    int mCurrentEntry = -1;
    private Realm mRealm;
    private int mFirstBeforePause;
    private int mLastBeforePause;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FeederApplication.getBus().register(this);
        mRealm = Realm.getDefaultInstance();
        RealmResults<Entry> entries = mRealm.where(Entry.class).findAllSorted("mCreatedAt", Sort.DESCENDING);

        mAdapter = new RealmAdapter<Entry, EntryRowBinding>(EntryRowBinding.class, entries) {
            @Override
            public void onBindViewHolder(FeedViewHolder<EntryRowBinding> holder, Entry instance) {
                holder.getBinding().setEntry(instance);
                holder.itemView.setOnClickListener(v -> {
                    DetailFragment fragment = DetailFragment.create(holder.getAdapterPosition());
                    fragment.setTargetFragment(EntriesFragment.this, 0);
                    getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_content, fragment)
                        .addToBackStack(null)
                        .commit();
                });
                holder.getBinding().star.setOnClickListener(v -> {
                    boolean newState = !v.isSelected();
                    Entry.setStarred(mRealm, instance.getId(), newState);
                    v.setSelected(newState);
                });
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FeederApplication.getBus().unregister(this);
        mRealm.close();
    }


    @NonNull
    @Override
    protected EntriesFragmentBinding createBinding(final LayoutInflater inflater, final ViewGroup container) {
        EntriesFragmentBinding binding = EntriesFragmentBinding.inflate(inflater, container, false);
        binding.recyclerView.setAdapter(mAdapter);
        binding.toolbar.inflateMenu(R.menu.entries_menu);
        binding.toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.menu_refresh:
                    new SyncTask(getActivity()).start();
                    return true;
                default:
                    return false;
            }
        });
        return binding;
    }

    @Override
    public void onResume() {
        super.onResume();
        // if we changed page in the detail view, scroll to minimally make that view visible.
        // To do this we tracked the first and last visible rows before we left (because in this
        // method we're not laid out yet), and will assume this has not changed. If the phone
        // has rotated or resized we'll guess wrong here.
        if (mBinding != null && mCurrentEntry >= 0 && mFirstBeforePause >= 0 && mLastBeforePause >= 0) {
            if (mCurrentEntry < mFirstBeforePause) {
                mBinding.recyclerView.scrollToPosition(mCurrentEntry);
            } else if (mCurrentEntry > mLastBeforePause) {
                mBinding.recyclerView.scrollToPosition(mCurrentEntry - (mLastBeforePause - mFirstBeforePause));
            }
            mCurrentEntry = -1;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mBinding != null) {
            LinearLayoutManager manager = (LinearLayoutManager) mBinding.recyclerView.getLayoutManager();
            mFirstBeforePause = manager.findFirstCompletelyVisibleItemPosition();
            mLastBeforePause = manager.findLastCompletelyVisibleItemPosition();
        }
    }

    public void childScrolledTo(int position) {
        if (mBinding != null) {
            mBinding.recyclerView.scrollToPosition(position);
        } else {
            mCurrentEntry = position;
        }
    }


}