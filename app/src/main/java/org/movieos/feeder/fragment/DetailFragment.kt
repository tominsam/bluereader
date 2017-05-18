package org.movieos.feeder.fragment

import android.content.Intent
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.app.FragmentStatePagerAdapter
import android.support.v4.content.ContextCompat
import android.support.v4.view.ViewPager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.realm.Realm
import io.realm.RealmChangeListener
import org.movieos.feeder.R
import org.movieos.feeder.databinding.DetailFragmentBinding
import org.movieos.feeder.model.Entry

class DetailFragment : DataBindingFragment<DetailFragmentBinding>(), RealmChangeListener<Realm> {

    internal var adapter: FragmentStatePagerAdapter? = null
    private val realm = Realm.getDefaultInstance()
    private var entryIds: List<Int> = ArrayList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entryIds = arguments.getIntegerArrayList(ENTRY_IDS) ?: ArrayList()

        // Watch all realm objects for changes
        realm.addChangeListener(this)

        adapter = object : FragmentStatePagerAdapter(childFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return DetailPageFragment.create(entryIds[position])
            }

            override fun getCount(): Int {
                return entryIds.size
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        realm?.close()
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
                    (targetFragment as EntriesFragment).childDisplayedEntryId(entryIds[position])
                }
            }

            override fun onPageScrollStateChanged(state: Int) {}
        })
        return binding
    }

    override fun onResume() {
        super.onResume()
        updateMenu()
        if (arguments.containsKey(INITIAL_ENTRY)) {
            val index = entryIds.indexOf(arguments.getInt(INITIAL_ENTRY))
            if (index >= 0 && index < entryIds.size) {
                binding?.viewPager?.setCurrentItem(index, false)
            }
            arguments.remove(INITIAL_ENTRY)
        }
    }

    internal fun updateMenu() {
        val binding = binding ?: return
        val entry = Entry.byId(realm, entryIds[binding.viewPager.currentItem]) ?: return

        val starred = binding.toolbar.menu.findItem(R.id.menu_star)
        val star = ContextCompat.getDrawable(context, if (entry.locallyStarred) R.drawable.ic_star_24dp else R.drawable.ic_star_border_24dp)
        star.setTint(0xFFFFFFFF.toInt())
        starred.icon = star

        val unread = binding.toolbar.menu.findItem(R.id.menu_unread)
        val circle = ContextCompat.getDrawable(context, if (entry.locallyUnread) R.drawable.ic_remove_circle_black_24dp else R.drawable.ic_remove_circle_outline_black_24dp)
        circle.setTint(0xFFFFFFFF.toInt())
        unread.icon = circle

        binding.toolbar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_star -> Entry.setStarred(context, realm, entry, !entry.locallyStarred)
                R.id.menu_unread -> Entry.setUnread(context, realm, entry, !entry.locallyUnread)
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

    override fun onChange(element: Realm?) {
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