package comx.y.z.kotlinbase.fragment.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.jibase.extensions.viewBinding
import com.jibase.utils.Log
import comx.y.z.kotlinbase.R
import comx.y.z.kotlinbase.databinding.FragmentPagerBinding

class PagerFragment : Fragment(R.layout.fragment_pager) {
    private val binding by viewBinding(FragmentPagerBinding::bind)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewPager.adapter = HomePager(
            childFragmentManager,
            listOf(
                PagerItemFragment().apply {
                    i = 0
                },
                PagerItemFragment().apply {
                    i = 1
                },
                PagerItemFragment().apply {
                    i = 2
                },
                PagerItemFragment().apply {
                    i = 3
                },
                PagerItemFragment().apply {
                    i = 4
                },
            )
        )

        binding.btn.setOnClickListener { binding.viewPager.currentItem = 0 }
    }
}