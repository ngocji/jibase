package comx.y.z.kotlinbase.fragment.list

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.jibase.iflexible.adapter.FlexibleAdapter
import com.jibase.iflexible.items.abstractItems.AbstractFlexibleItem
import com.jibase.iflexible.items.interfaceItems.IFilterable
import com.jibase.iflexible.viewholder.FlexibleViewHolder
import comx.y.z.kotlinbase.databinding.ItemListBinding

data class ItemList(val i: Int) : AbstractFlexibleItem<ItemList.ViewHolder>(), IFilterable {
    override fun filter(constraint: String): Boolean {
        return i.toString().contains(constraint)
    }

    override fun createViewHolder(parent: ViewGroup, adapter: FlexibleAdapter<*>): ViewHolder {
        return ViewHolder(
            ItemListBinding.inflate(LayoutInflater.from(parent.context), parent, false),
            adapter
        )
    }

    override fun bindViewHolder(
        adapter: FlexibleAdapter<*>,
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>
    ) {
        (holder as ViewHolder).binding.tv.text = i.toString()
    }

    class ViewHolder(val binding: ItemListBinding, adapter: FlexibleAdapter<*>) :
        FlexibleViewHolder(binding.root, adapter, false)
}