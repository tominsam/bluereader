package org.movieos.bluereader.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.ViewGroup
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.dao.MainDatabase
import org.movieos.bluereader.databinding.DetailFragmentBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.utilities.Web

private const val INITIAL_INDEX = "initial_index"

class DetailFragment : DataBindingFragment<DetailFragmentBinding>() {

    val database: MainDatabase
        get() = (activity.application as MainApplication).database

    val entriesFragment: EntriesFragment
        get() = targetFragment as EntriesFragment

    fun currentEntry(): Entry? {
        val position = binding?.viewPager?.currentItem ?: -1
        try {
            val entryId = entriesFragment.entriesAdapter.getItemId(position - 1)
            return database.entryDao().entryById(entryId.toInt())
        } catch (_: IndexOutOfBoundsException) {
            return null
        }
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailFragmentBinding {
        val binding = DetailFragmentBinding.inflate(inflater, container, false)

        binding.toolbarBack.setOnClickListener { activity.onBackPressed() }

        binding.viewPager.adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
            override fun getCount(): Int {
                return entriesFragment.entriesAdapter.itemCount + 2
            }

            override fun getItem(position: Int): Fragment {
                val itemId = if (position == 0 || position == count - 1) {
                    -1
                } else {
                    entriesFragment.entriesAdapter.getItemId(position - 1).toInt()
                }
                return DetailPageFragment.create(itemId)
            }
        }

        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                entriesFragment.childDisplayedEntryId(currentEntry()?.id ?: -1)
                if (isResumed)
                    updateToolbar()
            }
            override fun onPageScrollStateChanged(state: Int) {}
        })

        binding.toolbarStarred.setOnClickListener { v ->
            val current = currentEntry()
            if (current != null) {
                database.entryDao().setStarred(current.id, !current.starred)
                v.isSelected = !current.starred
            }
        }
        binding.toolbarUnread.setOnClickListener { v ->
            val current = currentEntry()
            if (current != null) {
                database.entryDao().setUnread(current.id, !current.unread)
                v.isSelected = !current.unread
            }
        }
        binding.toolbarOpen.setOnClickListener {
            val current = currentEntry()
            if (current != null)
                Web.openInBrowser(activity, current.url)
        }
        binding.toolbarShare.setOnClickListener {
            if (currentEntry() != null) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, currentEntry()!!.url)
                val chooser = Intent.createChooser(shareIntent, null)
                startActivity(chooser)
            }
        }
        binding.background.setOnClickListener {
            activity.onBackPressed()
        }

        return binding
    }

    override fun onResume() {
        super.onResume()
        if (arguments.containsKey(INITIAL_INDEX)) {
            binding?.viewPager?.setCurrentItem(arguments.getInt(INITIAL_INDEX) + 1, false)
            arguments.remove(INITIAL_INDEX)
        } else {
            // need to fire selectedPage to render the menu properly
            updateToolbar()
        }
    }

    private fun updateToolbar() {
        val current = currentEntry()
        if (current == null) {
            activity?.onBackPressed()
            return
        }
        binding?.entry = current
    }

    companion object {
        fun create(currentIndex: Int): DetailFragment {
            val fragment = DetailFragment()
            fragment.arguments = Bundle()
            fragment.arguments.putInt(INITIAL_INDEX, currentIndex)
            return fragment
        }
    }
}