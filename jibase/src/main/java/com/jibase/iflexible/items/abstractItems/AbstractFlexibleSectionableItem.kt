package com.jibase.iflexible.items.abstractItems

import androidx.recyclerview.widget.RecyclerView
import com.jibase.iflexible.items.interfaceItems.IHeader
import com.jibase.iflexible.items.interfaceItems.ISectionable

@Suppress("UNCHECKED_CAST")
abstract class AbstractFlexibleSectionableItem<VH : RecyclerView.ViewHolder, H : IHeader<*>> : AbstractFlexibleItem<VH>(), ISectionable<VH, H> {
    open var preHeader: H? = null

    init {
        setSelectable(true)
    }

    override fun getHeader(): H? = preHeader

    override fun setHeader(header: IHeader<*>?) {
        preHeader = header as? H
    }
}