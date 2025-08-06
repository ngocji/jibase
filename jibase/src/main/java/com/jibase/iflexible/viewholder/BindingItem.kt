@file:Suppress("UNCHECKED_CAST")

package com.jibase.iflexible.viewholder

import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.jibase.iflexible.adapter.FlexibleAdapter
import com.jibase.iflexible.items.abstractItems.AbstractFlexibleItem

abstract class BindingItem : AbstractFlexibleItem<BindingViewHolder>()

class BindingViewHolder(
    val binding: ViewBinding,
    adapter: FlexibleAdapter<*>,
    isStickyHeader: Boolean = false
) : FlexibleViewHolder(binding.root, adapter, isStickyHeader)


fun <V : ViewBinding> RecyclerView.ViewHolder.withBinding(action: V.() -> Unit) {
    ((this as? BindingViewHolder)?.binding as? V)?.also(action)
}