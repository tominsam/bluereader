package org.movieos.feeder.utilities

import android.databinding.ViewDataBinding
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import io.realm.OrderedCollectionChangeSet
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.RealmObject
import io.realm.RealmResults
import org.movieos.feeder.model.IntegerPrimaryKey
import java.lang.reflect.InvocationTargetException


abstract class RealmAdapter<T, B>(
        internal val klass: Class<B>,
        internal var query: RealmResults<T>
) : RecyclerView.Adapter<RealmAdapter.FeedViewHolder<B>>(), OrderedRealmCollectionChangeListener<RealmResults<T>>
where T : RealmObject, T : IntegerPrimaryKey, B : ViewDataBinding {

    init {
        @Suppress("LeakingThis")
        setHasStableIds(true)
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        query.addChangeListener(this)
        if (recyclerView.layoutManager == null) {
            recyclerView.layoutManager = LinearLayoutManager(recyclerView.context)
        }
    }

    fun setQuery(newQuery: RealmResults<T>) {
        query.removeChangeListener(this)
        newQuery.addChangeListener(this)
        query = newQuery
        notifyDataSetChanged()
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView?) {
        super.onDetachedFromRecyclerView(recyclerView)
        query.removeChangeListener(this)
    }

    override fun onChange(collection: RealmResults<T>, changeSet: OrderedCollectionChangeSet) {
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int {
        return query.size
    }

    override fun getItemId(position: Int): Long {
        return query[position].id.toLong()
    }

    val ids: List<Int>
        get() = query.map { t -> t.id }

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder<B> {
        val inflater = LayoutInflater.from(parent.context)
        try {
            val binding = klass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType).invoke(null, inflater, parent, false) as B
            return FeedViewHolder(binding)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to instantiate " + klass, e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Failed to instantiate " + klass, e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to instantiate " + klass, e)
        }

    }

    override fun onBindViewHolder(holder: FeedViewHolder<B>, position: Int) {
        val instance = query[position]
        onBindViewHolder(holder, instance)
    }

    abstract fun onBindViewHolder(holder: FeedViewHolder<B>, instance: T)

    class FeedViewHolder<out B : ViewDataBinding>(val binding: B) : RecyclerView.ViewHolder(binding.root)

}
