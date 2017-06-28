package org.movieos.bluereader.utilities

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.databinding.EntryRowBinding
import org.movieos.bluereader.model.Entry

class EntriesAdapter(
        val tapListener: (entry: Entry, index: Int) -> Unit,
        val starListener: (entry: Entry, newValue: Boolean) -> Boolean
) : RecyclerView.Adapter<BindingAdapter.FeedViewHolder>() {

    var rows: List<Int> = listOf()
        set(e) {
            field = e
            notifyDataSetChanged()
        }

    init {
        setHasStableIds(true)
    }

    override fun getItemCount(): Int {
        return rows.size
    }

    override fun getItemId(position: Int): Long {
        return rows[position].toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BindingAdapter.FeedViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = EntryRowBinding.inflate(inflater, parent, false)
        return BindingAdapter.FeedViewHolder(binding)
    }

    override fun onBindViewHolder(holder: BindingAdapter.FeedViewHolder, position: Int) {
        val binding = holder.itemView.tag as EntryRowBinding
        val entryId = rows[position]
        val database = (holder.itemView.context.applicationContext as MainApplication).database
        val entry = database.entryDao().entryById(entryId)
        if (entry != null) {
            binding.entry = entry
            binding.subscription = database.entryDao().subscriptionForFeed(binding.entry?.feedId ?: -1)
            binding.executePendingBindings()
            holder.itemView.setOnClickListener { tapListener.invoke(entry, holder.adapterPosition) }
            binding.star.setOnClickListener { binding.star.isSelected = starListener.invoke(entry, !it.isSelected) }
        }
    }
}
