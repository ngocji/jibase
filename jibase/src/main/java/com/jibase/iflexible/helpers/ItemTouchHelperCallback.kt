package com.jibase.iflexible.helpers

import android.graphics.Canvas
import android.view.View
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.jibase.extensions.gone
import com.jibase.extensions.visible
import com.jibase.iflexible.utils.LayoutUtils

open class ItemTouchHelperCallback(val adapterCallBack: AdapterCallback) :
    ItemTouchHelper.Callback() {
    var longPressDragEnabled = false
    var handleDragEnabled = false
    var swipeEnabled = false

    val ALPHA_FULL = 1.0f
    var SWIPE_DURATION = 300L
    var DRAG_DURATION = 400L

    var SWIPE_THRESHOLD = 0.5f
    var MOVE_THRESHOLD = 0.5f
    var swipeFlags = -1

    override fun isLongPressDragEnabled(): Boolean {
        return longPressDragEnabled
    }

    override fun isItemViewSwipeEnabled(): Boolean {
        return swipeEnabled;
    }

    override fun getMovementFlags(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder
    ): Int {
        val layoutManager = recyclerView.layoutManager
        var dragFlags: Int
        var swipeFlags: Int
        // Set movement flags based on the Layout Manager and Orientation
        if (layoutManager is GridLayoutManager || layoutManager is StaggeredGridLayoutManager) {
            dragFlags =
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            swipeFlags = 0
        } else if (LayoutUtils.getOrientation(recyclerView) == LinearLayoutManager.HORIZONTAL) {
            dragFlags = ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
            swipeFlags =
                if (this.swipeFlags > 0) this.swipeFlags else ItemTouchHelper.UP or ItemTouchHelper.DOWN
        } else {
            dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN
            swipeFlags =
                if (this.swipeFlags > 0) this.swipeFlags else ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        }
        // Disallow item swiping or dragging
        if (viewHolder is ViewHolderCallback) {
            val viewHolderCallback = viewHolder as ViewHolderCallback
            if (!viewHolderCallback.isDraggable()) dragFlags = 0
            if (!viewHolderCallback.isSwipeable()) swipeFlags = 0
        }
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        if (!adapterCallBack.shouldMove(
                viewHolder.absoluteAdapterPosition,
                target.absoluteAdapterPosition
            )
        ) {
            return false
        }
        // Notify the adapter of the move
        adapterCallBack.onItemMove(
            viewHolder.absoluteAdapterPosition,
            target.absoluteAdapterPosition
        )
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        if (viewHolder is ViewHolderCallback) {
            val viewHolderCallback = viewHolder as ViewHolderCallback
            if (viewHolderCallback.getFrontView().translationX != 0f)
                adapterCallBack.onItemSwiped(
                    viewHolder.absoluteAdapterPosition,
                    direction
                )
        }
    }

    override fun canDropOver(
        recyclerView: RecyclerView,
        current: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        return true
    }

    override fun getAnimationDuration(
        recyclerView: RecyclerView,
        animationType: Int,
        animateDx: Float,
        animateDy: Float
    ): Long {
        return if (animationType == ItemTouchHelper.ANIMATION_TYPE_DRAG) DRAG_DURATION else SWIPE_DURATION
    }

    override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        adapterCallBack.onActionStateChanged(viewHolder, actionState)
        // We only want the active item to change
        if (actionState != ItemTouchHelper.ACTION_STATE_IDLE) {
            if (viewHolder is ViewHolderCallback) {
                // Let the ViewHolder to know that this item is swiping or dragging
                val viewHolderCallback = viewHolder as ViewHolderCallback
                viewHolderCallback.onActionStateChanged(
                    viewHolder.absoluteAdapterPosition,
                    actionState
                )
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    ItemTouchHelper.Callback.getDefaultUIUtil()
                        .onSelected(viewHolderCallback.getFrontView())
                }
            }
        } else {
            super.onSelectedChanged(viewHolder, actionState)
        }
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        viewHolder.itemView.alpha = ALPHA_FULL
        if (viewHolder is ViewHolderCallback) {
            // Tell the view holder it's time to restore the idle state
            val viewHolderCallback = viewHolder as ViewHolderCallback
            getDefaultUIUtil().clearView(viewHolderCallback.getFrontView())
            // Hide Left or Right View
            setLayoutVisibility(viewHolderCallback, 0)
            viewHolderCallback.onItemReleased(viewHolder.absoluteAdapterPosition)
        }
    }

    override fun onChildDraw(
        c: Canvas,
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        dX: Float,
        dY: Float,
        actionState: Int,
        isCurrentlyActive: Boolean
    ) {
        if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && viewHolder is ViewHolderCallback) {

            // Update visibility for RearViews - Convert to custom VH
            val viewHolderCallback = viewHolder as ViewHolderCallback
            val frontView = viewHolderCallback.getFrontView()

            // Orientation independent
            var dragAmount = dX
            if (dY != 0f) dragAmount = dY

            // Manage opening - Is Left or Right View?
            var swipingDirection = 0  //0 is to reset the frontView
            if (dragAmount > 0) {
                swipingDirection = ItemTouchHelper.RIGHT  //DOWN
            } else if (dragAmount < 0) {
                swipingDirection = ItemTouchHelper.LEFT  //TOP
            }

            setLayoutVisibility(viewHolderCallback, swipingDirection)
            // Translate the FrontView
            getDefaultUIUtil().onDraw(
                c,
                recyclerView,
                frontView,
                dX,
                dY,
                actionState,
                isCurrentlyActive
            )

        } else {
            super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    /*------------------*/
    /*  Private method  */
    /*------------------*/

    open fun setLayoutVisibility(viewHolderCallback: ViewHolderCallback, swipeDirection: Int) {
        viewHolderCallback.getRearLeftView()?.apply {
            if (swipeDirection == ItemTouchHelper.RIGHT) visible() else gone()
        }
        viewHolderCallback.getRearRightView()?.apply {
            if (swipeDirection == ItemTouchHelper.LEFT) visible() else gone()
        }
    }

    /*------------------*/
    /* INNER INTERFACES */
    /*------------------*/


    interface AdapterCallback {
        /**
         * Called when the [ItemTouchHelper] first registers an item as being moved or swiped
         * or when has been released.
         *
         * Override this method to receive touch events with its state.
         *
         * @param viewHolder  the viewHolder touched
         * @param actionState one of [ItemTouchHelper.ACTION_STATE_SWIPE] or
         * [ItemTouchHelper.ACTION_STATE_DRAG] or
         * [ItemTouchHelper.ACTION_STATE_IDLE].
         */
        fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int)

        /**
         * Evaluate if positions are compatible for swapping.
         *
         * @param fromPosition the start position of the moving item
         * @param toPosition   the potential target position of the moving item
         * @return true if the from-item is allowed to swap with the to-item
         */
        fun shouldMove(fromPosition: Int, toPosition: Int): Boolean

        /**
         * Called when an item has been dragged far enough to trigger a move. **This is called
         * every time an item is shifted**, and **not** at the end of a "drop" event.
         *
         * Implementations should call [Adapter.notifyItemMoved]
         * after adjusting the underlying data to reflect this move.
         *
         * @param fromPosition the start position of the moved item
         * @param toPosition   the resolved position of the moved item
         * @return true if the from-item has been swapped with the to-item
         */
        fun onItemMove(fromPosition: Int, toPosition: Int): Boolean

        /**
         * Called when an item has been dismissed by a swipe.
         *
         * Implementations should decide to call or not [Adapter.notifyItemRemoved]
         * after adjusting the underlying data to reflect this removal.
         *
         * @param position  the position of the item dismissed
         * @param direction the direction to which the ViewHolder is swiped
         */
        fun onItemSwiped(position: Int, direction: Int)
    }

    interface ViewHolderCallback {

        /**
         * @return true if the view is draggable, false otherwise
         */
        fun isDraggable(): Boolean

        /**
         * @return true if the view is swipeable, false otherwise
         */
        fun isSwipeable(): Boolean

        /**
         * On Swipe, override to return the Front View.
         *
         * Default is itemView.
         *
         * @return the item Front View
         */
        fun getFrontView(): View

        /**
         * On Swipe, override to return the Rear Left View.
         *
         * Default is null (no view).
         *
         * @return the Rear Left View
         */
        fun getRearLeftView(): View?

        /**
         * On Swipe, override to return the Rear Right View.
         *
         * Default is null (no view).
         *
         * @return the Rear Right View
         */
        fun getRearRightView(): View?

        /**
         * Called when the [ItemTouchHelper] first registers an item as being moved or swiped.
         * <br></br>Implementations should update the item view to indicate it's active state.
         *
         * [FlexibleViewHolder] class already provides an implementation to handle the
         * active state.
         *
         * @param position    the position of the item touched
         * @param actionState one of [ItemTouchHelper.ACTION_STATE_SWIPE] or
         * [ItemTouchHelper.ACTION_STATE_DRAG].
         * @see FlexibleViewHolder.onActionStateChanged
         */
        fun onActionStateChanged(position: Int, actionState: Int)

        /**
         * Called when the [ItemTouchHelper] has completed the move or swipe, and the active
         * item state should be cleared.
         *
         * [FlexibleViewHolder] class already provides an implementation to disable the
         * active state.
         *
         * @param position the position of the item released
         */
        fun onItemReleased(position: Int)
    }
}