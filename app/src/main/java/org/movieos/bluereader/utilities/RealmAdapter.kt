package org.movieos.bluereader.utilities

import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import io.realm.RealmObject
import org.movieos.bluereader.model.IntegerPrimaryKey
import java.lang.reflect.InvocationTargetException


abstract class RealmAdapter<T, B>(
        internal val bindingClass: Class<B>,
        internal var rowClass: Class<T>
) : RecyclerView.Adapter<RealmAdapter.FeedViewHolder<B>>()
where T : RealmObject, T : IntegerPrimaryKey, B : ViewDataBinding {

    var entries: List<T>? = null
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    init {
        @Suppress("LeakingThis")
        setHasStableIds(true)
    }

    override fun getItemCount(): Int {
        return entries?.size ?: 0
    }

    override fun getItemId(position: Int): Long {
        return entries!![position].id.toLong()
    }

    val ids: List<Int>
        get() = entries?.map { it.id } ?: listOf()

    @Suppress("UNCHECKED_CAST")
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder<B> {
        val inflater = LayoutInflater.from(parent.context)
        try {
            val binding = bindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType).invoke(null, inflater, parent, false) as B
            return FeedViewHolder(binding)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to instantiate " + bindingClass, e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Failed to instantiate " + bindingClass, e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to instantiate " + bindingClass, e)
        }

    }

    override fun onBindViewHolder(holder: FeedViewHolder<B>, position: Int) {
        val instance = entries!![position]
        onBindViewHolder(holder, instance)
    }

    abstract fun onBindViewHolder(holder: FeedViewHolder<B>, instance: T)

    class FeedViewHolder<out B : ViewDataBinding>(val binding: B) : RecyclerView.ViewHolder(binding.root)

}
