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

private const val INITIAL_ENTRY = "current_entry"
private const val ENTRY_IDS = "entry_ids"

class DetailFragment : DataBindingFragment<DetailFragmentBinding>() {

    val realm: Realm = Realm.getDefaultInstance()
    var entryIds: List<Int> = listOf()
    var currentEntry: Entry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entryIds = arguments.getIntegerArrayList(ENTRY_IDS) ?: ArrayList()
    }

    override fun onDestroy() {
        super.onDestroy()
        realm.close()
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailFragmentBinding {
        val binding = DetailFragmentBinding.inflate(inflater, container, false)

        binding.toolbarBack.setOnClickListener { activity.onBackPressed() }

        binding.viewPager.adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
            override fun getCount(): Int {
                return entryIds.size
            }

            override fun getItem(position: Int): Fragment {
                return DetailPageFragment.create(entryIds[position])
            }
        }

        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}
            override fun onPageSelected(position: Int) = selectedPage(position)
            override fun onPageScrollStateChanged(state: Int) {}
        })

        binding.toolbarStarred.setOnClickListener {
            if (currentEntry != null)
                Entry.setStarred(context, realm, currentEntry!!, !currentEntry!!.starred)
        }
        binding.toolbarUnread.setOnClickListener {
            if (currentEntry != null)
                Entry.setUnread(context, realm, currentEntry!!, !currentEntry!!.unread)
        }
        binding.toolbarOpen.setOnClickListener {
            if (currentEntry != null)
                Web.openInBrowser(activity, currentEntry!!.url)
        }
        binding.toolbarShare.setOnClickListener {
            if (currentEntry != null) {
                val shareIntent = Intent(Intent.ACTION_SEND)
                shareIntent.type = "text/plain"
                shareIntent.putExtra(Intent.EXTRA_TEXT, currentEntry!!.url)
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

    override fun onPause() {
        super.onPause()
        currentEntry?.removeAllChangeListeners()
        currentEntry = null
    }

    private fun selectedPage(position: Int) {
        val entryId = entryIds[position]
        Timber.i("Selected position $position == entry $entryId")
        if (targetFragment is EntriesFragment) {
            (targetFragment as EntriesFragment).childDisplayedEntryId(entryId)
        }
        currentEntry?.removeAllChangeListeners()
        if (entryId == -1) {
            activity.onBackPressed()
        } else {
            currentEntry = Entry.byId(realm, entryId).findFirstAsync()
            currentEntry?.addChangeListener { _: Entry -> render() }
        }
    }

    internal fun render() {
        Timber.i("Rendering $currentEntry")
        binding?.entry = currentEntry
    }

    companion object {
       fun create(entryIds: List<Int>, currentEntryId: Int): DetailFragment {
            val fragment = DetailFragment()
            fragment.arguments = Bundle()
            fragment.arguments.putIntegerArrayList(ENTRY_IDS, ArrayList(entryIds))
            fragment.arguments.putInt(INITIAL_ENTRY, currentEntryId)
            return fragment
        }
    }
}