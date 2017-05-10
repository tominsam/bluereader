package org.movieos.feeder.utilities;

import android.databinding.ViewDataBinding;
import android.support.annotation.NonNull;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import org.movieos.feeder.sync.IntegerPrimaryKey;

import java.lang.reflect.InvocationTargetException;

import io.realm.RealmObject;
import io.realm.RealmResults;


public abstract class RealmAdapter<T extends RealmObject & IntegerPrimaryKey, B extends ViewDataBinding> extends RecyclerView.Adapter<RealmAdapter.FeedViewHolder<B>> {

    Class<B> mKlass;
    RealmResults<T> mQuery;

    public RealmAdapter(Class<B> klass, RealmResults<T> query) {
        mKlass = klass;
        mQuery = query;
        mQuery.addChangeListener((collection, changeSet) -> notifyDataSetChanged());
        setHasStableIds(true);
    }

    @Override
    public void onAttachedToRecyclerView(RecyclerView recyclerView) {
        super.onAttachedToRecyclerView(recyclerView);
        if (recyclerView.getLayoutManager() == null) {
            recyclerView.setLayoutManager(new LinearLayoutManager(recyclerView.getContext()));
        }
    }

    @Override
    public int getItemCount() {
        return mQuery.size();
    }

    @Override
    public long getItemId(int position) {
        return mQuery.get(position).getId();
    }

    @SuppressWarnings("unchecked")
    @Override
    public FeedViewHolder<B> onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        try {
            B binding = (B) mKlass.getMethod("inflate", LayoutInflater.class, ViewGroup.class, boolean.class).invoke(null, inflater, parent, false);
            return new FeedViewHolder<>(binding);
        } catch (IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
            throw new RuntimeException("Failed to instantiate " + mKlass, e);
        }
    }

    @Override
    public void onBindViewHolder(FeedViewHolder<B> holder, int position) {
        T instance = mQuery.get(position);
        onBindViewHolder(holder, instance);
    }

    public abstract void onBindViewHolder(FeedViewHolder<B> holder, T instance);

    public final static class FeedViewHolder<B extends ViewDataBinding> extends RecyclerView.ViewHolder {
        @NonNull
        private B mBinding;

        protected FeedViewHolder(@NonNull B binding) {
            super(binding.getRoot());
            mBinding = binding;
        }

        @NonNull
        public B getBinding() {
            return mBinding;
        }
    }
}
