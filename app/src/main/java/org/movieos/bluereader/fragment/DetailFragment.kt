package org.movieos.bluereader.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.ViewGroup
import io.realm.Realm
import org.movieos.bluereader.databinding.DetailFragmentBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.utilities.Web
import timber.log.Timber

class DetailFragment : DataBindingFragment<DetailFragmentBinding>() {

    var adapter: FragmentStatePagerAdapter? = null
    val realm: Realm by lazy { Realm.getDefaultInstance() }
    var entryIds: List<Int> = ArrayList()
    var currentEntry: Entry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entryIds = arguments.getIntegerArrayList(ENTRY_IDS) ?: ArrayList()

        adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
            override fun getCount(): Int {
                return entryIds.size
            }

            override fun getItem(position: Int): Fragment {
                return DetailPageFragment.create(entryIds[position])
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailFragmentBinding {
        val binding = DetailFragmentBinding.inflate(inflater, container, false)

        binding.toolbarBack.setOnClickListener{  activity.onBackPressed() }

        binding.viewPager.adapter = adapter
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) = selectedPage(position)
            override fun onPageScrollStateChanged(state: Int) {}
        })
        return binding
    }

    private fun selectedPage(position: Int) {
        val entryId = entryIds[position]
        if (targetFragment is EntriesFragment) {
            (targetFragment as EntriesFragment).childDisplayedEntryId(entryId)
        }
        currentEntry?.removeAllChangeListeners()
        currentEntry = Entry.byId(realm, entryId).findFirstAsync()
        currentEntry?.addChangeListener { e: Entry -> updateMenu(e) }
    }

    override fun onResume() {
        super.onResume()
        if (arguments.containsKey(INITIAL_ENTRY)) {
            val index = entryIds.indexOf(arguments.getInt(INITIAL_ENTRY))
            if (index >= 0 && index < entryIds.size) {
                binding?.viewPager?.setCurrentItem(index, false)
                selectedPage(index)
            }
            arguments.remove(INITIAL_ENTRY)
        } else {
            // need to fire selectedPage to render the menu properly
            selectedPage(binding?.viewPager?.currentItem ?: 0)
        }
    }

    internal fun updateMenu(entry: Entry) {
        Timber.i("Selected $entry")
        val binding = binding ?: return
        binding.entry = entry
        binding.toolbarStarred.setOnClickListener { Entry.setStarred(context, realm, entry, !entry.starred) }
        binding.toolbarUnread.setOnClickListener { Entry.setUnread(context, realm, entry, !entry.unread) }
        binding.toolbarOpen.setOnClickListener { Web.openInBrowser(activity, entry.url) }
        binding.toolbarShare.setOnClickListener {
            val shareIntent = Intent(Intent.ACTION_SEND)
            shareIntent.type = "text/plain"
            shareIntent.putExtra(Intent.EXTRA_TEXT, entry.url)
            val chooser = Intent.createChooser(shareIntent, null)
            startActivity(chooser)
        }
    }

    companion object {

        private val INITIAL_ENTRY = "current_entry"
        private val ENTRY_IDS = "entry_ids"

        fun create(entryIds: List<Int>, currentEntryId: Int): DetailFragment {
            val fragment = DetailFragment()
            fragment.arguments = Bundle()
            fragment.arguments.putIntegerArrayList(DetailFragment.ENTRY_IDS, ArrayList(entryIds))
            fragment.arguments.putInt(INITIAL_ENTRY, currentEntryId)
            return fragment
        }
    }
}