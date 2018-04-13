package org.movieos.bluereader.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.PagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.ViewGroup
import org.movieos.bluereader.MainApplication
import org.movieos.bluereader.api.Mercury
import org.movieos.bluereader.dao.MainDatabase
import org.movieos.bluereader.databinding.DetailFragmentBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.utilities.Web

private const val INITIAL_INDEX = "initial_index"
private const val INITIAL_ENTRYIDS = "initial_entryids"

class DetailFragment : DataBindingFragment<DetailFragmentBinding>() {

    var entryIds: List<Int> = emptyList()

    val database: MainDatabase
        get() = (activity!!.application as MainApplication).database

    val entriesFragment: EntriesFragment
        get() = targetFragment as EntriesFragment

    fun currentEntry(): Entry? {
        return database.entryDao().entryById(currentEntryId())
    }

    fun currentEntryId(): Int {
        val position = binding?.viewPager?.currentItem ?: return -1
        return entryIds[position]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // different negative IDs because they're also the adapter item Ids.
        entryIds = arrayListOf(-1) + (arguments?.getIntegerArrayList(INITIAL_ENTRYIDS) ?: emptyList<Int>()) + arrayListOf(-2)
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailFragmentBinding {
        val binding = DetailFragmentBinding.inflate(inflater, container, false)

        binding.toolbarBack.setOnClickListener { activity?.onBackPressed() }

        binding.viewPager.adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
            override fun getCount(): Int {
                return entryIds.size
            }

            override fun getItem(position: Int): Fragment {
                val itemId = entryIds[position]
                return DetailPageFragment.create(itemId)
            }

            override fun getItemPosition(item: Any): Int {
                return PagerAdapter.POSITION_NONE
            }
        }

        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) {
                entriesFragment.childDisplayedEntryId(currentEntryId())
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
                entriesFragment.childChangedEntryState()
            }
        }
        binding.toolbarUnread.setOnClickListener { v ->
            val current = currentEntry()
            if (current != null) {
                database.entryDao().setUnread(current.id, !current.unread)
                v.isSelected = !current.unread
                entriesFragment.childChangedEntryState()
            }
        }
        binding.toolbarOpen.setOnClickListener {
            val current = currentEntry()
            if (current != null)
                Web.openInBrowser(activity!!, current.url)
        }
        binding.toolbarShare.setOnClickListener {
            val entry = currentEntry()
            if (entry != null) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, entry.url)
                val chooser = Intent.createChooser(shareIntent, null)
                startActivity(chooser)
            }
        }
        binding.toolbarMercury.setOnClickListener {
            val entry = currentEntry()
            if (Mercury(context!!).contentFor(entry) != null) {
                Mercury(context!!).clearContent(entry)
                binding.viewPager.adapter?.notifyDataSetChanged()
                updateToolbar()

            } else {
                Mercury(context!!).parser(entry) {
                    binding.viewPager.adapter?.notifyDataSetChanged()
                    updateToolbar()
                }
            }
        }
        binding.background.setOnClickListener {
            activity?.onBackPressed()
        }

        return binding
    }

    override fun onResume() {
        super.onResume()
        if (arguments?.containsKey(INITIAL_INDEX) == true) {
            binding?.viewPager?.setCurrentItem(arguments!!.getInt(INITIAL_INDEX) + 1, false)
            arguments!!.remove(INITIAL_INDEX)
        } else {
            // need to fire selectedPage to render the menu properly
            updateToolbar()
        }
        entriesFragment.childDisplayedEntryId(currentEntryId())
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
        fun create(entryIds: List<Int>, currentIndex: Int): DetailFragment {
            val fragment = DetailFragment()
            fragment.arguments = Bundle()
            fragment.arguments!!.putInt(INITIAL_INDEX, currentIndex)
            fragment.arguments!!.putIntegerArrayList(INITIAL_ENTRYIDS, ArrayList(entryIds))
            return fragment
        }
    }
}