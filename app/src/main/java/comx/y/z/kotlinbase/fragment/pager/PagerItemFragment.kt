package comx.y.z.kotlinbase.fragment.pager

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.jibase.extensions.showToast
import com.jibase.extensions.viewBinding
import com.jibase.utils.Log
import comx.y.z.kotlinbase.R
import comx.y.z.kotlinbase.databinding.FragmentPagerItemBinding

class PagerItemFragment() : Fragment(R.layout.fragment_pager_item) {
    private val binding by viewBinding(FragmentPagerItemBinding::bind)
    var i: Int = 0
    override fun toString(): String {
        return "${i}"
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d("PagerFragment: onViewCreated: ${this}, $view")
        binding.tv.setText("Test Fragment: ${i}")
        binding.btn.setOnClickListener {
            binding.tv.setText("${System.currentTimeMillis()}")
        }
    }
}