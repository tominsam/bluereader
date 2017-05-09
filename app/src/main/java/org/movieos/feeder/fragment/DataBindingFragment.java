package org.movieos.feeder.fragment;

import android.databinding.ViewDataBinding;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

public abstract class DataBindingFragment<B extends ViewDataBinding> extends Fragment {

    @Nullable
    protected B mBinding;

    @Nullable
    @Override
    public View onCreateView(final LayoutInflater inflater, @Nullable final ViewGroup container, @Nullable final Bundle savedInstanceState) {
        mBinding = createBinding(inflater, container);
        return mBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mBinding != null) {
            mBinding.unbind();
            mBinding = null;
        }
    }

    @NonNull
    protected abstract B createBinding(LayoutInflater inflater, ViewGroup container);

    @Nullable
    public B getBinding() {
        return mBinding;
    }
}
