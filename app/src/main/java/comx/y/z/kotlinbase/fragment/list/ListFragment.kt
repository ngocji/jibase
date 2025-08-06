package comx.y.z.kotlinbase.fragment.list

import android.os.Bundle
import android.view.View
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.jibase.extensions.viewBinding
import com.jibase.iflexible.adapter.FlexibleAdapter
import comx.y.z.kotlinbase.R
import comx.y.z.kotlinbase.databinding.FragmentListBinding

class ListFragment : Fragment(R.layout.fragment_list) {
    private val binding by viewBinding(FragmentListBinding::bind)
    private var flexibleAdapter: FlexibleAdapter<ItemList>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        initViews()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        flexibleAdapter?.release()
    }

    private fun initRecyclerView() {
        val items = mutableListOf<ItemList>()
        for (i in 0..100) {
            items.add(ItemList(i))
        }
        flexibleAdapter = FlexibleAdapter(items)
        binding.recyclerView.adapter = flexibleAdapter
    }

    private fun initViews() {
        binding.edtQuery.doAfterTextChanged {
            val text = it.toString()
            flexibleAdapter?.setFilter(text)
            flexibleAdapter?.filterItems(
                5000
            )
        }
    }
}