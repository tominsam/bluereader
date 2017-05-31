package org.movieos.feeder.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.ViewGroup
import io.realm.Realm
import org.movieos.feeder.R
import org.movieos.feeder.databinding.DetailFragmentBinding
import org.movieos.feeder.model.Entry
import org.movieos.feeder.utilities.Web
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

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_control_24dp)
        binding.toolbar.setNavigationOnClickListener { activity.onBackPressed() }

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

        // reset menu completely every time, so we can be sure we never generate it in a weird half-state
        binding.toolbar.menu.clear()
        binding.toolbar.inflateMenu(R.menu.detail_menu)
        val starred = binding.toolbar.menu.findItem(R.id.menu_star)
        val star = ContextCompat.getDrawable(context, if (entry.starred) R.drawable.ic_star_24dp else R.drawable.ic_star_border_24dp)
        star.setTint(0xFFFFFFFF.toInt())
        starred.icon = star

        val unread = binding.toolbar.menu.findItem(R.id.menu_unread)
        val circle = ContextCompat.getDrawable(context, if (entry.unread) R.drawable.ic_remove_circle_black_24dp else R.drawable.ic_remove_circle_outline_black_24dp)
        circle.setTint(0xFFFFFFFF.toInt())
        unread.icon = circle

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_star -> Entry.setStarred(context, realm, entry, !entry.starred)
                R.id.menu_unread -> Entry.setUnread(context, realm, entry, !entry.unread)
                R.id.menu_open -> Web.openInBrowser(activity, entry.url)
                R.id.menu_share -> {
                    val shareIntent = Intent(Intent.ACTION_SEND)
                    shareIntent.type = "text/plain"
                    shareIntent.putExtra(Intent.EXTRA_TEXT, entry.url)
                    val chooser = Intent.createChooser(shareIntent, null)
                    startActivity(chooser)
                }
            }
            true
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