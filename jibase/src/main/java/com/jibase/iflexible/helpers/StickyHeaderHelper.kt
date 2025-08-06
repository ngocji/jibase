@file:Suppress("UNCHECKED_CAST")

package com.jibase.iflexible.helpers

import android.animation.Animator
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.view.*
import androidx.recyclerview.widget.OrientationHelper
import androidx.recyclerview.widget.RecyclerView
import com.jibase.extensions.invisible
import com.jibase.extensions.visible
import com.jibase.iflexible.adapter.FlexibleAdapter
import com.jibase.iflexible.items.interfaceItems.IFlexible
import com.jibase.iflexible.listener.OnStickyHeaderChangeListener
import com.jibase.iflexible.viewholder.FlexibleViewHolder
import com.jibase.utils.Log

class StickyHeaderHelper<T : IFlexible<*>>(
        private val mAdapter: FlexibleAdapter<T>,
        private val mStickyHeaderChangeListener: OnStickyHeaderChangeListener?,
        private var mStickyHolderLayout: ViewGroup?
) : RecyclerView.OnScrollListener() {

    private lateinit var mRecyclerView: RecyclerView
    private var mHeaderPosition = RecyclerView.NO_POSITION
    private var displayWithAnimation = false
    private var mElevation = 0f
    private var mStickyHeaderViewHolder: FlexibleViewHolder? = null


    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        displayWithAnimation = mRecyclerView.scrollState == RecyclerView.SCROLL_STATE_IDLE
        updateOrClearHeader(false)
    }


    /*******************/
    /* Public method  */
    /*****************/

    fun attachToRecyclerView(parent: RecyclerView) {
        if (this::mRecyclerView.isInitialized) {
            mRecyclerView.removeOnScrollListener(this)
            clearHeader()
        }
        mRecyclerView = parent
        mRecyclerView.addOnScrollListener(this)
        initStickyHeadersHolder()
    }

    fun detachFromRecyclerView() {
        mRecyclerView.removeOnScrollListener(this)
        clearHeaderWithAnimation()
        Log.d("StickyHolderLayout detached")
    }

    fun getStickyPosition(): Int {
        return mHeaderPosition
    }

    fun updateOrClearHeader(updateHeaderContent: Boolean) {
        Log.d("updateOrClearHeader $updateHeaderContent")
        if (!mAdapter.areHeadersShown() || mAdapter.itemCount == 0) {
            clearHeaderWithAnimation()
            return
        }
        val firstHeaderPosition = getStickyPosition(RecyclerView.NO_POSITION)
        if (firstHeaderPosition >= 0) {
            Log.d("updateOrClearHeader $updateHeaderContent ---> $firstHeaderPosition")
            updateHeader(firstHeaderPosition, updateHeaderContent)
        } else {
            clearHeader()
        }
    }

    fun ensureHeaderParent() {
        mStickyHeaderViewHolder?.also { holder ->
            holder.itemView.layoutParams.width = holder.contentView.measuredWidth
            holder.itemView.layoutParams.height = holder.contentView.measuredHeight
            // Ensure the itemView is hidden to avoid double background
            holder.itemView.invisible()
            Log.d("Ensure headerParent: ${holder.itemView.measuredWidth} / ${holder.itemView.measuredHeight} / ${holder.itemView.visibility}")
            applyLayoutParamsAndMargins(holder.contentView)
            removeViewFromParent(holder.contentView)
            mStickyHolderLayout?.also {
                addViewToParent(it, holder.contentView)
            }
            configureLayoutElevation()
        }
    }


    fun clearHeaderWithAnimation() {
        if (mStickyHeaderViewHolder != null && mHeaderPosition != RecyclerView.NO_POSITION) {
            Log.d("clear header with animation")
            mStickyHolderLayout?.animate()?.setListener(object : Animator.AnimatorListener {
                override fun onAnimationStart(animation: Animator) {
                    mHeaderPosition = RecyclerView.NO_POSITION
                }

                override fun onAnimationEnd(animation: Animator) {
                    displayWithAnimation = true //This helps after clearing filter
                    mStickyHolderLayout?.alpha = 0f
                    clearHeader()
                }

                override fun onAnimationCancel(animation: Animator) {}

                override fun onAnimationRepeat(animation: Animator) {}
            })
            mStickyHolderLayout?.animate()?.alpha(0f)?.start()
        }
    }

    fun clearHeader() {
        mStickyHeaderViewHolder?.also {
            resetHeader(it)
            mStickyHolderLayout?.apply {
                alpha = 0f
                animate().cancel()
                animate().setListener(null)
            }
            Log.d("clearHeader")
            mStickyHeaderViewHolder = null
            restoreHeaderItemVisibility()
            val oldPosition = mHeaderPosition
            mHeaderPosition = RecyclerView.NO_POSITION
            onStickyHeaderChange(mHeaderPosition, oldPosition)
        }
    }

    /*******************/
    /* Private method  */
    /*****************/
    private fun createContainer(width: Int, height: Int): FrameLayout {
        val frameLayout = FrameLayout(mRecyclerView.context)
        frameLayout.layoutParams = ViewGroup.MarginLayoutParams(width, height)
        return frameLayout
    }

    private fun getParent(view: View): ViewGroup {
        return view.parent as ViewGroup
    }

    private fun initStickyHeadersHolder() {
        if (mStickyHolderLayout == null) {
            val oldParentLayout = getParent(mRecyclerView)
            // Initialize Holder Layout, will be also used for elevation
            mStickyHolderLayout = createContainer(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            )
            oldParentLayout.addView(mStickyHolderLayout)
            Log.d("Default StickyHolderLayout initialized")
        } else {
            Log.d("User defined StickyHolderLayout initialized")
        }
        displayWithAnimation = true

        // Show sticky header if exists already
        updateOrClearHeader(false)
    }

    private fun hasStickyHeaderTranslated(position: Int): Boolean {
        val vh = mRecyclerView.findViewHolderForAdapterPosition(position)
        return vh != null && (vh.itemView.x < 0 || vh.itemView.y < 0)
    }

    private fun onStickyHeaderChange(newPosition: Int, oldPosition: Int) {
        mStickyHeaderChangeListener?.onStickyHeaderChange(mAdapter, newPosition, oldPosition)
    }

    private fun updateHeader(headerPosition: Int, updateHeaderContent: Boolean) {
        if (mHeaderPosition != headerPosition) {
            val firstVisibleItemPosition =
                    mAdapter.getFlexibleLayoutManager().findFirstVisibleItemPosition()
            if (displayWithAnimation && mHeaderPosition == RecyclerView.NO_POSITION &&
                    headerPosition != firstVisibleItemPosition
            ) {
                displayWithAnimation = false
                mStickyHolderLayout?.apply {
                    alpha = 0f
                    animate().alpha(1f).start()
                }
            } else {
                mStickyHolderLayout?.alpha = 1f
            }
            val oldHeaderPosition = mHeaderPosition
            mHeaderPosition = headerPosition
            val holder = getHeaderViewHolder(headerPosition)
            swapHeader(holder, oldHeaderPosition)
        } else if (updateHeaderContent) {
            if (mStickyHeaderViewHolder?.itemViewType == mAdapter.getItemViewType(headerPosition)) {
                mStickyHeaderViewHolder?.also {
                    mAdapter.onBindViewHolder(it, headerPosition)
                }
                Log.d("updateHeader content ended")
            } else {
                Log.d("updateHeader Wrong itemViewType for StickyViewHolder")
            }
            ensureHeaderParent()
        }
        translateHeader()
    }

    private fun configureLayoutElevation() {
        // 1. Take elevation from header item layout (most important)
        mStickyHeaderViewHolder?.contentView?.also { holder ->
            mElevation = ViewCompat.getElevation(holder)
            if (mElevation == 0f) {
                // 2. Take elevation settings
                mElevation =
                        mRecyclerView.context.resources.displayMetrics.density * mAdapter.getStickyHeaderElevation()
            }
            if (mElevation > 0) {
                // Needed to elevate the view
                mStickyHolderLayout?.also {
                    it.background = holder.background
                }
            }
        }
    }

    private fun translateHeader() {
        mRecyclerView.layoutManager?.also { manager ->
            // Sticky at zero offset (no translation)
            var headerOffsetX = 0
            var headerOffsetY = 0
            // Get calculated elevation
            var elevation = mElevation

            // Search for the position where the next header item is found and translate the new offset
            for (i in 0 until mRecyclerView.childCount) {
                val nextChild = mRecyclerView.getChildAt(i)
                if (nextChild != null) {
                    val adapterPos = mRecyclerView.getChildAdapterPosition(nextChild)
                    val nextHeaderPosition = getStickyPosition(adapterPos)
                    if (mHeaderPosition != nextHeaderPosition) {
                        if (mAdapter.getFlexibleLayoutManager()
                                        .getOrientation() == OrientationHelper.HORIZONTAL
                        ) {
                            if (nextChild.left > 0) {
                                val headerWidth = mStickyHolderLayout?.measuredWidth ?: 0
                                val nextHeaderOffsetX = nextChild.left - headerWidth -
                                        manager.getLeftDecorationWidth(nextChild) -
                                        manager.getRightDecorationWidth(nextChild)
                                headerOffsetX = Math.min(nextHeaderOffsetX, 0)
                                // Early remove the elevation/shadow to match with the next view
                                if (nextHeaderOffsetX < 5) elevation = 0f
                                if (headerOffsetX < 0) break
                            }
                        } else {
                            if (nextChild.top > 0) {
                                val headerHeight = mStickyHolderLayout?.measuredHeight ?: 0
                                val nextHeaderOffsetY = nextChild.top - headerHeight -
                                        manager.getTopDecorationHeight(nextChild) -
                                        manager.getBottomDecorationHeight(nextChild)
                                headerOffsetY = Math.min(nextHeaderOffsetY, 0)
                                // Early remove the elevation/shadow to match with the next view
                                if (nextHeaderOffsetY < 5) elevation = 0f
                                if (headerOffsetY < 0) break
                            }
                        }
                    }
                }
            }
            // Apply the user elevation to the sticky container
            mStickyHolderLayout?.apply {
                ViewCompat.setElevation(this, elevation)
                // Apply translation (pushed up by another header)
                translationX = headerOffsetX.toFloat()
                translationY = headerOffsetY.toFloat()
                Log.d("Translate header: $headerOffsetX / $headerOffsetY --> ${this.measuredWidth} / ${this.measuredHeight}  --> ${this.childCount}")
            }
        }
    }

    private fun swapHeader(newHeader: FlexibleViewHolder, oldHeaderPosition: Int) {
        Log.d("swapHeader newHeaderPosition=%s $mHeaderPosition")
        mStickyHeaderViewHolder?.also {
            resetHeader(it)
            // #568, #575 - Header ViewHolder out of the top screen must be recycled manually
            if (mHeaderPosition > oldHeaderPosition) {
                mAdapter.onViewRecycled(it)
            }
        }

        mStickyHeaderViewHolder = newHeader
        mStickyHeaderViewHolder?.setIsRecyclable(false)
        ensureHeaderParent()
        onStickyHeaderChange(mHeaderPosition, oldHeaderPosition)
    }

    private fun applyLayoutParamsAndMargins(view: View) {
        val manager = mRecyclerView.layoutManager
        val itemView = mStickyHeaderViewHolder?.itemView
        if (manager != null && itemView != null) {
            (mStickyHolderLayout?.layoutParams as? ViewGroup.MarginLayoutParams)?.apply {
                width = view.measuredWidth
                height = view.measuredHeight
                // Margins from current offset
                if (leftMargin == 0)
                    leftMargin = manager.getLeftDecorationWidth(itemView)
                if (topMargin == 0)
                    topMargin = manager.getTopDecorationHeight(itemView)
                if (rightMargin == 0)
                    rightMargin = manager.getRightDecorationWidth(itemView)
                if (bottomMargin == 0)
                    bottomMargin = manager.getBottomDecorationHeight(itemView)
            }
            Log.d("UpdateLayoutParams; ${mStickyHolderLayout?.measuredWidth} / ${mStickyHolderLayout?.measuredHeight} \n--> margin: ${mStickyHolderLayout?.marginStart}/${mStickyHolderLayout?.marginTop}/${mStickyHolderLayout?.marginRight}/${mStickyHolderLayout?.marginBottom}")
        }
    }

    /**
     * On swing and on fast scroll some header items might still be invisible. We need
     * to identify them and restore visibility.
     */
    private fun restoreHeaderItemVisibility() {
        // Restore every header item visibility
        for (i in 0 until mRecyclerView.childCount) {
            val oldHeader = mRecyclerView.getChildAt(i)
            val headerPos = mRecyclerView.getChildAdapterPosition(oldHeader)
            if (mAdapter.isHeader(mAdapter.getItem(headerPos))) {
                oldHeader.visible()
            }
        }
        Log.d("restore header item")
    }

    private fun resetHeader(header: FlexibleViewHolder) {
        restoreHeaderItemVisibility()
        // Clean the header container
        val view = header.contentView
        removeViewFromParent(view)
        // Reset translation on removed header
        view.translationX = 0f
        view.translationY = 0f
        if (header.itemView != view) {
            addViewToParent(header.itemView as ViewGroup, view)
        }
        header.setIsRecyclable(true)

        // - Expandable header is not resized / redrawn on automatic configuration change when sticky headers are enabled
        header.itemView.layoutParams.width = view.layoutParams.width
        header.itemView.layoutParams.height = view.layoutParams.height
        Log.d("ResetHeader ${view.layoutParams.width} / ${view.layoutParams.height}")
    }

    private fun addViewToParent(parent: ViewGroup, child: View) {
        try {
            parent.addView(child)
            Log.d("AddView to parent")
        } catch (e: IllegalStateException) {
            Log.d("The specified child already has a parent! (but parent was removed!)")
        }

    }

    private fun removeViewFromParent(view: View) {
        val parent = view.parent
        if (parent is ViewGroup) {
            Log.d("RemoveView to parent")
            parent.removeView(view)
        }
    }

    private fun getStickyPosition(adapterPosHere: Int): Int {
        var position = adapterPosHere
        if (position == RecyclerView.NO_POSITION) {
            position = mAdapter.getFlexibleLayoutManager().findFirstVisibleItemPosition()
            if (position == 0 && !hasStickyHeaderTranslated(0)) {
                return RecyclerView.NO_POSITION
            }
        }
        val header = mAdapter.getSectionHeader(position)
        // Header cannot be sticky if it's also an Expandable in collapsed status, RV will raise an exception
        return if (header == null || mAdapter.isExpandableItem(header as? T) && !mAdapter.isExpanded(
                        header as? T)) {
            RecyclerView.NO_POSITION
        } else mAdapter.getGlobalPositionOf(header as T)
    }

    /**
     * Gets the header view for the associated header position. If it doesn't exist yet, it will
     * be created, measured, and laid out.
     *
     * @param position the adapter position to get the header view
     * @return ViewHolder of type FlexibleViewHolder of the associated header position
     */
    private fun getHeaderViewHolder(position: Int): FlexibleViewHolder {
        // Find existing ViewHolder
        var holder = mRecyclerView.findViewHolderForAdapterPosition(position) as? FlexibleViewHolder

        if (holder == null) {
            // Create and binds a new ViewHolder
            holder = mAdapter.createViewHolder(
                    mRecyclerView,
                    mAdapter.getItemViewType(position)
            ) as FlexibleViewHolder
            // Skip ViewHolder caching by setting not recyclable
            holder.setIsRecyclable(false)
            mAdapter.bindViewHolder(holder, position)
            holder.setIsRecyclable(true)

            // Calculate width and height
            val widthSpec: Int
            val heightSpec: Int
            if (mAdapter.getFlexibleLayoutManager()
                            .getOrientation() == OrientationHelper.VERTICAL
            ) {
                widthSpec =
                        View.MeasureSpec.makeMeasureSpec(mRecyclerView.width, View.MeasureSpec.EXACTLY)
                heightSpec = View.MeasureSpec.makeMeasureSpec(
                        mRecyclerView.height,
                        View.MeasureSpec.UNSPECIFIED
                )
            } else {
                widthSpec = View.MeasureSpec.makeMeasureSpec(
                        mRecyclerView.width,
                        View.MeasureSpec.UNSPECIFIED
                )
                heightSpec =
                        View.MeasureSpec.makeMeasureSpec(mRecyclerView.height, View.MeasureSpec.EXACTLY)
            }

            // Measure and Layout the stickyView
            val headerView = holder.contentView
            val childWidth = ViewGroup.getChildMeasureSpec(
                    widthSpec,
                    mRecyclerView.paddingLeft + mRecyclerView.paddingRight,
                    headerView.layoutParams.width
            )
            val childHeight = ViewGroup.getChildMeasureSpec(
                    heightSpec,
                    mRecyclerView.paddingTop + mRecyclerView.paddingBottom,
                    headerView.layoutParams.height
            )

            headerView.measure(childWidth, childHeight)
            headerView.layout(0, 0, headerView.measuredWidth, headerView.measuredHeight)
            Log.d("GetHeaderViewHolder $childWidth / $childHeight")
        }
        // Be sure VH has the backup Adapter position
        holder.backupPosition = position
        return holder
    }

}