package org.movieos.feeder.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import io.realm.Realm
import io.realm.RealmChangeListener
import io.realm.RealmResults
import org.movieos.feeder.R
import org.movieos.feeder.databinding.DetailFragmentBinding
import org.movieos.feeder.model.Entry
import timber.log.Timber
import java.util.*

class DetailFragment : DataBindingFragment<DetailFragmentBinding>(), RealmChangeListener<RealmResults<Entry>> {

    internal var adapter: FragmentStatePagerAdapter? = null
    private var realm: Realm? = null
    private var entryIds: List<Int>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        realm = Realm.getDefaultInstance()
        entryIds = arguments.getIntegerArrayList(ENTRY_IDS)
        // Watch all realm objects for changes
        realm!!.where(Entry::class.java).findAll().addChangeListener(this)

        adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return DetailPageFragment.create(entryIds!![position])
            }

            override fun getCount(): Int {
                return entryIds!!.size
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm!!.close()
    }

    override fun createBinding(inflater: LayoutInflater, container: ViewGroup?): DetailFragmentBinding {
        val binding = DetailFragmentBinding.inflate(inflater, container, false)

        binding.toolbar.setNavigationIcon(R.drawable.ic_arrow_back_control_24dp)
        binding.toolbar.setNavigationOnClickListener { v: View -> activity.onBackPressed() }
        binding.toolbar.inflateMenu(R.menu.detail_menu)

        binding.viewPager.adapter = adapter
        binding.viewPager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                updateMenu()
                if (targetFragment is EntriesFragment) {
                    (targetFragment as EntriesFragment).childDisplayedEntryId(entryIds!![position])
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        return binding
    }

    override fun onResume() {
        super.onResume()
        updateMenu()
        if (binding != null && arguments.containsKey(INITIAL_ENTRY)) {
            val index = entryIds!!.indexOf(arguments.getInt(INITIAL_ENTRY))
            if (index >= 0 && index < entryIds!!.size) {
                binding!!.viewPager.setCurrentItem(index, false)
            }
            arguments.remove(INITIAL_ENTRY)
        }
    }

    internal fun updateMenu() {
        Timber.i("Updating menu")
        if (binding == null) {
            return
        }
        val entry = Entry.byId(entryIds!![binding!!.viewPager.currentItem])

        val starred = binding!!.toolbar.menu.findItem(R.id.menu_star)
        val star = ContextCompat.getDrawable(context, if (entry!!.isLocallyStarred) R.drawable.ic_star_24dp else R.drawable.ic_star_border_24dp)
        star.setTint(0xFFFFFFFF.toInt())
        starred.icon = star

        val unread = binding!!.toolbar.menu.findItem(R.id.menu_unread)
        val circle = ContextCompat.getDrawable(context, if (entry.isLocallyUnread) R.drawable.ic_remove_circle_black_24dp else R.drawable.ic_remove_circle_outline_black_24dp)
        circle.setTint(0xFFFFFFFF.toInt())
        unread.icon = circle

        binding!!.toolbar.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_star -> Entry.setStarred(context, realm!!, entry, !entry.isLocallyStarred)
                R.id.menu_unread -> Entry.setUnread(context, realm!!, entry, !entry.isLocallyUnread)
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

    override fun onChange(element: RealmResults<Entry>) {
        updateMenu()
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