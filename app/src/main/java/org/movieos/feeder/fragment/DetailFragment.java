package org.movieos.feeder.fragment;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.movieos.feeder.FeederApplication;
import org.movieos.feeder.R;
import org.movieos.feeder.databinding.DetailFragmentBinding;
import org.movieos.feeder.model.Entry;

import io.realm.Realm;
import io.realm.RealmResults;
import io.realm.Sort;
import timber.log.Timber;

public class DetailFragment extends DataBindingFragment<DetailFragmentBinding> {

    private static final String INDEX = "index";
    FragmentStatePagerAdapter mAdapter;
    private Realm mRealm;
    private RealmResults<Entry> mEntries;

    public static DetailFragment create(int index) {
        DetailFragment fragment = new DetailFragment();
        fragment.setArguments(new Bundle());
        fragment.getArguments().putInt(INDEX, index);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FeederApplication.getBus().register(this);

        mAdapter = new FragmentStatePagerAdapter(getChildFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                return DetailPageFragment.create(position);
            }

            @Override
            public int getCount() {
                return mEntries.size();
            }
        };

        mRealm = Realm.getDefaultInstance();
        mEntries = mRealm.where(Entry.class).findAllSorted("mCreatedAt", Sort.DESCENDING);
        mEntries.addChangeListener((collection, changeSet) -> mAdapter.notifyDataSetChanged());
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        FeederApplication.getBus().unregister(this);
        mEntries.removeAllChangeListeners();
        mRealm.close();
    }

    @NonNull
    @Override
    protected DetailFragmentBinding createBinding(final LayoutInflater inflater, final ViewGroup container) {
        DetailFragmentBinding binding = DetailFragmentBinding.inflate(inflater, container, false);

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_control_24dp);
        binding.toolbar.setNavigationOnClickListener(v -> getActivity().onBackPressed());

        binding.viewPager.setAdapter(mAdapter);
        binding.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                Entry.setUnread(mRealm, mEntries.get(position).getId(), false);
                getArguments().putInt(INDEX, position);
                if (mBinding != null) {
                    mBinding.toolbar.setTitle(mEntries.get(position).getTitle());
                }
                if (getTargetFragment() instanceof EntriesFragment) {
                    ((EntriesFragment) getTargetFragment()).childScrolledTo(position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });
        return binding;
    }

    @Override
    public void onResume() {
        super.onResume();
        int index = getArguments().getInt(INDEX);
        Timber.i("index is %d", index);
        if (mBinding != null) {
            mBinding.viewPager.setCurrentItem(index, false);
            Entry.setUnread(mRealm, mEntries.get(index).getId(), false);
        }
    }

    public Entry getEntry(int position) {
        return mEntries.get(position);
    }

}