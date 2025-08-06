package comx.y.z.kotlinbase.fragment.pager

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter

class HomePager(
    fragmentManager: FragmentManager,
    private val items: List<Fragment>
) : FragmentStatePagerAdapter(fragmentManager, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

    override fun getCount(): Int {
        return items.size
    }

    override fun getItem(position: Int): Fragment {
        return items[position]
    }
}