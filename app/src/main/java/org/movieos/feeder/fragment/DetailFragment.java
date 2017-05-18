package org.movieos.feeder.fragment;

import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.ViewGroup;

import org.jetbrains.annotations.NotNull;
import org.movieos.feeder.R;
import org.movieos.feeder.databinding.DetailFragmentBinding;
import org.movieos.feeder.model.Entry;

import java.util.ArrayList;
import java.util.List;

import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;
import timber.log.Timber;

public class DetailFragment extends DataBindingFragment<DetailFragmentBinding> implements RealmChangeListener<RealmResults<Entry>> {

    private static final String INITIAL_ENTRY = "current_entry";
    private static final String ENTRY_IDS = "entry_ids";

    FragmentStatePagerAdapter mAdapter;
    private Realm mRealm;
    private List<Integer> mEntryIds;

    public static DetailFragment create(List<Integer> entryIds, int currentEntryId) {
        DetailFragment fragment = new DetailFragment();
        fragment.setArguments(new Bundle());
        fragment.getArguments().putIntegerArrayList(DetailFragment.ENTRY_IDS, new ArrayList<>(entryIds));
        fragment.getArguments().putInt(INITIAL_ENTRY, currentEntryId);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRealm = Realm.getDefaultInstance();
        mEntryIds = getArguments().getIntegerArrayList(ENTRY_IDS);
        // Watch all realm objects for changes
        mRealm.where(Entry.class).findAll().addChangeListener(this);

        mAdapter = new FragmentStatePagerAdapter(getChildFragmentManager()) {
            @Override
            public Fragment getItem(int position) {
                return DetailPageFragment.create(mEntryIds.get(position));
            }

            @Override
            public int getCount() {
                return mEntryIds.size();
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mRealm.close();
    }

    @NonNull
    @Override
    protected DetailFragmentBinding createBinding(@NotNull final LayoutInflater inflater, @org.jetbrains.annotations.Nullable final ViewGroup container) {
        DetailFragmentBinding binding = DetailFragmentBinding.inflate(inflater, container, false);

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_control_24dp);
        binding.toolbar.setNavigationOnClickListener(v -> getActivity().onBackPressed());
        binding.toolbar.inflateMenu(R.menu.detail_menu);

        binding.viewPager.setAdapter(mAdapter);
        binding.viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                updateMenu();
                if (getTargetFragment() instanceof EntriesFragment) {
                    ((EntriesFragment) getTargetFragment()).childDisplayedEntryId(mEntryIds.get(position));
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
        updateMenu();
        if (getBinding() != null && getArguments().containsKey(INITIAL_ENTRY)) {
            int index = mEntryIds.indexOf(getArguments().getInt(INITIAL_ENTRY));
            if (index >= 0 && index < mEntryIds.size()) {
                getBinding().viewPager.setCurrentItem(index, false);
            }
            getArguments().remove(INITIAL_ENTRY);
        }
    }

    void updateMenu() {
        Timber.i("Updating menu");
        if (getBinding() == null) {
            return;
        }
        Entry entry = Entry.Companion.byId(mEntryIds.get(getBinding().viewPager.getCurrentItem()));

        MenuItem starred = getBinding().toolbar.getMenu().findItem(R.id.menu_star);
        Drawable star = ContextCompat.getDrawable(getContext(), entry.isLocallyStarred() ? R.drawable.ic_star_24dp : R.drawable.ic_star_border_24dp);
        star.setTint(0xFFFFFFFF);
        starred.setIcon(star);

        MenuItem unread = getBinding().toolbar.getMenu().findItem(R.id.menu_unread);
        Drawable circle = ContextCompat.getDrawable(getContext(), entry.isLocallyUnread() ? R.drawable.ic_remove_circle_black_24dp: R.drawable.ic_remove_circle_outline_black_24dp);
        circle.setTint(0xFFFFFFFF);
        unread.setIcon(circle);

        getBinding().toolbar.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case R.id.menu_star:
                    Entry.Companion.setStarred(getContext(), mRealm, entry, !entry.isLocallyStarred());
                    break;
                case R.id.menu_unread:
                    Entry.Companion.setUnread(getContext(), mRealm, entry, !entry.isLocallyUnread());
                    break;
                case R.id.menu_share:
                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                    shareIntent.setType("text/plain");
                    shareIntent.putExtra(Intent.EXTRA_TEXT, entry.getUrl());
                    Intent chooser = Intent.createChooser(shareIntent, null);
                    startActivity(chooser);
                    break;
            }
            return true;
        });

    }

    @Override
    public void onChange(RealmResults<Entry> element) {
        updateMenu();
    }
}