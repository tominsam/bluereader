package org.movieos.bluereader.utilities

import android.databinding.ViewDataBinding
import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import java.lang.reflect.InvocationTargetException


class BindingAdapter : RecyclerView.Adapter<BindingAdapter.FeedViewHolder>() {

    data class Row(val bindingClass: Class<ViewDataBinding>, val id: Int, val bind : (ViewDataBinding, View) -> Unit)

    class FeedViewHolder(val binding: ViewDataBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            itemView.setTag(binding)
        }
    }

    class Builder {
        val rows : MutableList<Row> = mutableListOf()

        fun <B : ViewDataBinding> addRow(klass: Class<B>, id: Int, bind: (B, View) -> Unit) {
            rows += Row(klass as Class<ViewDataBinding>, id, bind as (ViewDataBinding, View) -> Unit)
        }
    }

    var bindingClasses: List<Class<ViewDataBinding>> = mutableListOf()

    private var rows: List<Row> = ArrayList()
        set(value) {
            field = value
            for (row in value) {
                if (!bindingClasses.contains(row.bindingClass)) {
                    bindingClasses += row.bindingClass
                }
            }
            notifyDataSetChanged()
        }

    init {
        @Suppress("LeakingThis")
        setHasStableIds(true)
    }

    override fun getItemViewType(position: Int): Int {
        return bindingClasses.indexOf(rows[position].bindingClass)
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    override fun getItemId(position: Int): Long {
        return rows[position].id.toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FeedViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val bindingClass = bindingClasses[viewType]
        try {
            val binding = bindingClass.getMethod("inflate", LayoutInflater::class.java, ViewGroup::class.java, Boolean::class.javaPrimitiveType).invoke(null, inflater, parent, false) as ViewDataBinding
            return FeedViewHolder(binding)
        } catch (e: IllegalAccessException) {
            throw RuntimeException("Failed to instantiate " + bindingClass, e)
        } catch (e: NoSuchMethodException) {
            throw RuntimeException("Failed to instantiate " + bindingClass, e)
        } catch (e: InvocationTargetException) {
            throw RuntimeException("Failed to instantiate " + bindingClass, e)
        }
    }

    override fun onBindViewHolder(holder: FeedViewHolder, position: Int) {
        val instance = rows[position]
        instance.bind(holder.itemView.tag as ViewDataBinding, holder.itemView)
    }

    fun fromBuilder(builder: Builder) {
        rows = builder.rows
    }

}
