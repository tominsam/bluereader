package org.movieos.feeder.fragment;

import android.support.annotation.NonNull;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.movieos.feeder.databinding.SyncFragmentBinding;
import org.movieos.feeder.sync.Sync;


public class SyncFragment extends DataBindingFragment<SyncFragmentBinding> {
    @NonNull
    @Override
    protected SyncFragmentBinding createBinding(final LayoutInflater inflater, final ViewGroup container) {
        SyncFragmentBinding binding = SyncFragmentBinding.inflate(inflater, container, false);

        return binding;
    }

    @Override
    public void onResume() {
        super.onResume();

        new Sync(getActivity()).subscriptions();
    }
}
