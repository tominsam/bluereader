package org.movieos.bluereader.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.ViewGroup
import io.realm.Realm
import io.realm.RealmResults
import org.movieos.bluereader.databinding.DetailFragmentBinding
import org.movieos.bluereader.model.Entry
import org.movieos.bluereader.utilities.Web
import timber.log.Timber

private const val INITIAL_INDEX = "initial_index"

class DetailFragment : DataBindingFragment<DetailFragmentBinding>() {

    val realm: Realm = Realm.getDefaultInstance()
    // Watches for any changes to any and all entry objects
    val entryWatcher: RealmResults<Entry> = realm.where(Entry::class.java).findAllAsync()

    val entriesFragment: EntriesFragment
        get() = targetFragment as EntriesFragment

    init {
        // Every time any entries change, rebuld the displayed list. Not very efficient.
        entryWatcher.addChangeListener { _: RealmResults<Entry> ->
            Timber.i("realm changed")
            updateToolbar()
            binding?.viewPager?.adapter?.notifyDataSetChanged()
        }
    }

    fun currentEntry(): Entry? {
        val position = binding?.viewPager?.currentItem ?: -1
        try {
            val entryId = entriesFragment.entriesAdapter.getItemId(position - 1)
            return realm.where(Entry::class.java).equalTo("id", entryId).findFirst()
        } catch (_: ArrayIndexOutOfBoundsException) {
            return null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        entryWatcher.removeAllChangeListeners()
        realm.close()
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
                updateToolbar()
            }
            override fun onPageScrollStateChanged(state: Int) {}
        })

        binding.toolbarStarred.setOnClickListener { v ->
            val current = currentEntry()
            if (current != null) {
                Entry.setStarred(context, realm, current, !current.starred)
                v.isSelected = !current.starred
            }
        }
        binding.toolbarUnread.setOnClickListener { v ->
            val current = currentEntry()
            if (current != null) {
                Entry.setUnread(context, realm, current, !current.unread)
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