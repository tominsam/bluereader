package org.movieos.bluereader.utilities

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import io.realm.RealmResults
import org.movieos.bluereader.databinding.EntryRowBinding
import org.movieos.bluereader.model.Entry

class EntriesAdapter(
        val tapListener: (Entry) -> Unit,
        val starListener: (Entry, Boolean) -> Boolean
) : RecyclerView.Adapter<BindingAdapter.FeedViewHolder>() {

    var entries: RealmResults<Entry>? = null
        set(e) {
            field = e
            notifyDataSetChanged()
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int): Long {
        return entries!![position].id.toLong()
    }

    override fun getItemCount(): Int {
        return entries?.size ?: 0
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingAdapter.FeedViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = EntryRowBinding.inflate(inflater, parent, false)
        return BindingAdapter.FeedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingAdapter.FeedViewHolder, position: Int) {
        val binding = holder.itemView.tag as EntryRowBinding
        val entry = entries!![position]
        binding.entry = entry
        binding.executePendingBindings()
        holder.itemView.setOnClickListener { tapListener.invoke(entry) }
        binding.star.setOnClickListener { binding.star.isSelected = starListener.invoke(entry, !it.isSelected) }
    }
}
