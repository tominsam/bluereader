package org.movieos.feeder.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.movieos.feeder.FeederApplication;
import org.movieos.feeder.R;
import org.movieos.feeder.databinding.EntriesFragmentBinding;
import org.movieos.feeder.databinding.EntryRowBinding;
import org.movieos.feeder.sync.Entry;
import org.movieos.feeder.utilities.RealmAdapter;
import org.movieos.feeder.utilities.SyncTask;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;

public class EntriesFragment extends DataBindingFragment<EntriesFragmentBinding> {

    RealmAdapter<Entry, EntryRowBinding> mAdapter;
    private Realm mRealm;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FeederApplication.getBus().register(this);
        mRealm = Realm.getDefaultInstance();
        RealmResults<Entry> entries = mRealm.where(Entry.class).findAllSortedAsync("mCreatedAt", Sort.DESCENDING);

        mAdapter = new RealmAdapter<Entry, EntryRowBinding>(EntryRowBinding.class, entries) {
            @Override
            public void onBindViewHolder(FeedViewHolder<EntryRowBinding> holder, Entry instance) {
                holder.getBinding().setEntry(instance);
                holder.itemView.setOnClickListener(v -> {
                    getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.main_content, DetailFragment.create(holder.getAdapterPosition()))
                        .addToBackStack(null)
                        .commit();
                });
                holder.getBinding().star.setOnClickListener(v -> {
                    boolean newState = !v.isSelected();
                    Entry.setStarred(instance, newState);
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
    }


}