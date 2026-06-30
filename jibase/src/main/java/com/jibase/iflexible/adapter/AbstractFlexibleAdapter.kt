package com.jibase.iflexible.adapter

import android.os.Bundle
import androidx.recyclerview.widget.RecyclerView
import com.jibase.extensions.changeElevation
import com.jibase.iflexible.adapter.FlexibleAdapter.Companion.IDLE
import com.jibase.iflexible.adapter.FlexibleAdapter.Companion.SINGLE
import com.jibase.iflexible.common.FlexibleLayoutManager
import com.jibase.iflexible.common.IFlexibleLayoutManager
import com.jibase.iflexible.entities.Payload
import com.jibase.iflexible.fastscroll.FastScroller
import com.jibase.iflexible.viewholder.FlexibleViewHolder
import com.jibase.utils.Log
import java.util.*

abstract class AbstractFlexibleAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>(),
    FastScroller.BubbleTextCreator, FastScroller.OnScrollStateChangeListener,
    FastScroller.AdapterInterface {
    companion object {
        private const val TAG = "FlexibleAdapter"
    }

    open var mode = IDLE
    open lateinit var mFlexibleLayoutManager: IFlexibleLayoutManager
    open lateinit var mRecyclerView: RecyclerView

    // fast scroller
    open var mFastScrollerDelegate: FastScroller.Delegate = FastScroller.Delegate()
    private var backupFastScroller: FastScroller? = null

    private val selectedPositions = mutableSetOf<Int>()
    private val boundViewHolders = mutableSetOf<FlexibleViewHolder>()
    val configurations = mutableMapOf<String, Any>()


    /**
     * Flag when fast scrolling is active.
     *
     * Used to know if user is fast scrolling.
     */
    open var isFastScroll = false

    /**
     * ActionMode selection flag SelectAll.
     *
     * Used when user click on selectAll action button in ActionMode.
     */
    open var selectAll = false

    /**
     * ActionMode selection flag LastItemInActionMode.
     *
     * Used when user returns to [Mode.IDLE] and no selection is active.
     */
    open var lastItemInActionMode = false

    /**
     * Action mode flag start selection
     */
    open var isActionModeStateEnable = false

    /*--------------*/
    /* CONFIGURATIONS */
    /*--------------*/

    fun setConfig(key: String, value: Any) {
        configurations[key] = value
    }

    fun setConfigs(map: Map<String, Any>) {
        configurations.putAll(map)
    }

    inline fun <reified T> getConfig(key: String): T? {
        return configurations[key] as? T
    }

    fun removeConfig(key: String) {
        configurations.remove(key)
    }

    fun clearConfigs() {
        configurations.clear()
    }

    /*--------------*/
    /* CHECK INITIALIZED */
    /*--------------*/
    fun flexibleLayoutManagerHasInitialized(): Boolean {
        return this::mFlexibleLayoutManager.isInitialized
    }

    fun recyclerViewHasInitialized(): Boolean {
        return this::mRecyclerView.isInitialized
    }


    /*--------------*/
    /* MAIN METHODS */
    /*--------------*/


    /**
     * {@inheritDoc}
     *
     * Attaches the `FastScrollerDelegate` to the RecyclerView if necessary.
     *
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        mFastScrollerDelegate.onAttachedToRecyclerView(recyclerView)
        mRecyclerView = recyclerView
        mFlexibleLayoutManager = FlexibleLayoutManager(recyclerView)
        backupFastScroller?.also { setFastScroller(it) }
        super.onAttachedToRecyclerView(recyclerView)
    }

    /**
     * {@inheritDoc}
     *
     * Detaches the `FastScrollerDelegate` from the RecyclerView if necessary.
     *
     */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        mFastScrollerDelegate.onDetachedFromRecyclerView(recyclerView)
    }

    /**
     * @return the RecyclerView instance
     */
    fun getRecyclerView(): RecyclerView {
        return mRecyclerView
    }

    /**
     * Current instance of the wrapper class for LayoutManager suitable for FlexibleAdapter.
     * LayoutManager must be already initialized in the RecyclerView.
     *
     *
     * return wrapper class for any non-conventional LayoutManagers or `null` if not initialized.
     *
     */
    fun getFlexibleLayoutManager(): IFlexibleLayoutManager {
        return mFlexibleLayoutManager
    }

    /**
     * Allow to use a custom LayoutManager.
     *
     * @param flexibleLayoutManager the custom LayoutManager suitable for FlexibleAdapter
     */
    fun setFlexibleLayoutManager(flexibleLayoutManager: IFlexibleLayoutManager) {
        this.mFlexibleLayoutManager = flexibleLayoutManager
    }

    /**
     * @return true if user clicks on SelectAll on action button in ActionMode.
     */
    fun isSelectAll(): Boolean {
        // Reset the flags with delay
        resetActionModeFlags()
        return selectAll
    }

    /**
     * @return true if user returns to [Mode.IDLE] or [Mode.SINGLE] and no
     * selection is active, false otherwise
     */
    fun isLastItemInActionMode(): Boolean {
        // Reset the flags with delay
        resetActionModeFlags()
        return lastItemInActionMode
    }

    /**
     * Resets to false the ActionMode flags: `SelectAll` and `LastItemInActionMode`.
     */
    private fun resetActionModeFlags() {
        if (selectAll || lastItemInActionMode) {
            mRecyclerView.postDelayed({
                selectAll = false
                lastItemInActionMode = false
            }, 200L)
        }
    }

    /**
     * Indicates if the item, at the provided position, is selected.
     *
     * @param position Position of the item to check.
     * @return true if the item is selected, false otherwise.
     */
    fun isSelected(position: Int): Boolean {
        return selectedPositions.contains(position)
    }

    /**
     * Checks if the current item has the property `enableSelect = true`.
     *
     * @param position the current position of the item to check
     * @return true if the item property enableSelect is true, false otherwise
     */
    abstract fun isSelectable(position: Int): Boolean


    /**
     * Toggles the selection status of the item at a given position.
     *
     * The behaviour depends on the selection mode previously set with [.setMode].
     * The Activated State of the ItemView is automatically set in
     * [FlexibleViewHolder.toggleActivation] called in `onClick` event
     *
     * **Usage:**
     *
     *  * If you don't want any item to be selected/activated at all, just don't call this method.
     *  * To have actually the item visually selected you need to add a custom *Selector Drawable*
     * to the background of the View, via `DrawableUtils` or via layout's item:
     * *android:background="?attr/selectableItemBackground"*, pointing to a custom Drawable
     * in the style.xml (note: prefix *?android:attr* <u>doesn't</u> work).
     *  *
     *
     *
     * @param position Position of the item to toggle the selection status for.
     */
    open fun toggleSelection(position: Int) {
        if (position < 0) return
        if (mode == SINGLE) {
            clearSelection()
        }
        val contains = selectedPositions.contains(position)
        if (contains) {
            removeSelection(position, true)
        } else {
            addSelection(position, true)
        }
//        notifyItemChanged(position)
        Log.d(
            "toggleSelection $position on position $position, current $selectedPositions ," +
                    if (contains) "removed" else "added", TAG
        )
    }

    /**
     * Adds the selection status for the given position without notifying the change.
     *
     * @param position Position of the item to add the selection status for.
     * @return true if the set is modified, false otherwise or position is not currently enableSelect
     * @see .isSelectable
     */
    fun addSelection(position: Int, hasNotify: Boolean = false): Boolean {
        val modified = isSelectable(position) && selectedPositions.add(position)
        if (modified && hasNotify) notifyItemChanged(position, Payload.SELECTED)
        return modified
    }

    /**
     * This method is used only internally to force adjust selection.
     *
     * @param position Position of the item to add the selection status for.
     * @return true if the set is modified, false otherwise
     */
    fun addAdjustedSelection(position: Int): Boolean {
        return selectedPositions.add(position)
    }

    fun addAdjustedSelection(positions: List<Int>) {
        selectedPositions.addAll(positions)
    }

    /**
     * Removes the selection status for the given position without notifying the change.
     *
     * @param position Position of the item to remove the selection status for.
     * @return true if the set is modified, false otherwise
     */
    fun removeSelection(position: Int, hasNotify: Boolean = false): Boolean {
        val modified = selectedPositions.remove(position)
        if (modified && hasNotify) notifyItemChanged(position, Payload.UN_SELECTED)
        return modified
    }


    /**
     * Helper method to easily swap selection between 2 positions only if one of the positions
     * is *not* selected.
     *
     * @param fromPosition first position
     * @param toPosition   second position
     */
    protected fun swapSelection(fromPosition: Int, toPosition: Int) {
        if (isSelected(fromPosition) && !isSelected(toPosition)) {
            removeSelection(fromPosition)
            addSelection(toPosition)
        } else if (!isSelected(fromPosition) && isSelected(toPosition)) {
            removeSelection(toPosition)
            addSelection(fromPosition)
        }
    }

    /**
     * Sets the selection status for all items which the ViewTypes are included in the specified array.
     *
     * @param viewTypes The ViewTypes for which we want the selection, pass nothing to select all
     */
    open fun selectAll(vararg viewTypes: Int) {
        selectAll = true
        Log.d("selectAll ViewTypes to include $viewTypes", TAG)
        var positionStart = 0
        var itemCount = 0
        val payload = Payload.SELECTED
        for (i in 0 until getItemCount()) {
            if (isSelectable(i) && (viewTypes.isEmpty() || viewTypes.contains(getItemViewType(i)))) {
                selectedPositions.add(i)
                itemCount++
            } else {
                // Optimization for ItemRangeChanged
                if (positionStart + itemCount == i) {
                    notifySelectionChanged(positionStart, itemCount, payload)
                    itemCount = 0
                    positionStart = i
                }
            }
        }
        Log.d(
            "selectAll notifyItemRangeChanged from positionStart=${positionStart} itemCount=${getItemCount()} ---> ",
            TAG
        )
        notifySelectionChanged(positionStart, getItemCount(), payload)
    }

    /**
     * Clears the selection status for all items one by one and it doesn't stop animations in the items.
     *
     *
     * **Note 1:** Items are not rebound, so an eventual animation is not stopped!<br></br>
     * **Note 2:** This method use `java.util.Iterator` on synchronized collection to
     * avoid `java.util.ConcurrentModificationException`.
     *
     */
    open fun clearSelection() {
        // #373 - ConcurrentModificationException with Undo after multiple rapid swipe removals
        val payload = Payload.UN_SELECTED
        synchronized(selectedPositions) {
            Log.d("clearSelection $selectedPositions", TAG)
            val iterator = selectedPositions.iterator()
            var positionStart = 0
            var itemCount = 0
            // The notification is done only on items that are currently selected.
            while (iterator.hasNext()) {
                val position = iterator.next()
                iterator.remove()
                // Optimization for ItemRangeChanged
                if (positionStart + itemCount == position) {
                    itemCount++
                } else {
                    // Notify previous items in range
                    notifySelectionChanged(positionStart, itemCount, payload)
                    positionStart = position
                    itemCount = 1
                }
            }
            Log.d("clearSelection positionStart=$positionStart -> itemCount=$itemCount", TAG)
            // Notify remaining items in range
            notifySelectionChanged(positionStart, itemCount, payload)
        }
    }

    open fun clearAdjustSelection() {
        selectedPositions.clear()
    }

    private fun notifySelectionChanged(
        positionStart: Int,
        itemCount: Int,
        payload: Payload? = null
    ) {
        if (itemCount > 0) {
            // Avoid to rebind the VH, direct call to the itemView activation
            for (flexHolder in boundViewHolders) {
                flexHolder.toggleActivation()
            }
            // Use classic notification, in case FlexibleViewHolder is not implemented
            if (boundViewHolders.isEmpty()) {
                notifyItemRangeChanged(positionStart, itemCount, Payload.SELECTION)
            } else {
                notifyItemRangeChanged(positionStart, itemCount, payload)
            }
        }
    }


    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>
    ) {
        // Bind the correct view elevation
        if (holder is FlexibleViewHolder) {
            holder.contentView.isActivated = isSelected(position)
            if (holder.contentView.isActivated && holder.getActivationElevation() > 0) {
                holder.contentView.changeElevation(holder.getActivationElevation())
            } else if (holder.getActivationElevation() > 0) { // Leave unaltered the default elevation
                holder.contentView.changeElevation(0f)
            }
            if (holder.isRecyclable) {
                boundViewHolders.add(holder)
            }
        } else {
            // When user scrolls, this line binds the correct selection status
            holder.itemView.isActivated = isSelected(position)
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        if (holder is FlexibleViewHolder) {
            val recycled = boundViewHolders.remove(holder)
            Log.d(
                "onViewRecycled viewSize=${boundViewHolders.size} ---> $holder recycled=$recycled",
                TAG
            )
        }
    }


    /**
     * To call when views are all discarded.
     */
    open fun discardBoundViewHolders() {
        boundViewHolders.clear()
    }

    /**
     * Usually `RecyclerView` binds 3 items more than the visible items.
     *
     * @return a Set with all bound FlexibleViewHolders
     */
    fun getAllBoundViewHolders(): Set<FlexibleViewHolder> {
        return Collections.unmodifiableSet(boundViewHolders)
    }

    /**
     * Counts the selected items.
     *
     * @return Selected items count
     */
    fun getSelectedItemCount(): Int {
        return selectedPositions.size
    }

    /**
     * Retrieves the list of selected items.
     *
     * The list is a copy and it's sorted.
     *
     * @return A copied List of selected items ids from the Set
     */
    fun getSelectedPositions(): List<Int> {
        return selectedPositions.toList()
    }

    /**
     * Retrieves the set of selected items.
     *
     * The set is sorted.
     *
     * @return Set of selected items ids
     */
    fun getSelectedPositionsAsSet(): Set<Int> {
        return selectedPositions
    }


    /*----------------*/
    /* INSTANCE STATE */
    /*----------------*/

    /**
     * Saves the state of the current selection on the items.
     *
     * @param outState Current state
     */
    open fun onSaveInstanceState(outState: Bundle) {
        outState.putIntegerArrayList(javaClass.simpleName, ArrayList(selectedPositions))
        if (getSelectedItemCount() > 0) Log.d("Saving selection $selectedPositions")
    }

    /**
     * Restores the previous state of the selection on the items.
     *
     * @param savedInstanceState Previous state
     */
    open fun onRestoreInstanceState(savedInstanceState: Bundle) {
        // Fix for #651 - Check nullable: it happens that the list is null in some unknown cases
        val selectedItems = savedInstanceState.getIntegerArrayList(javaClass.simpleName)
        if (selectedItems != null) {
            selectedPositions.addAll(selectedItems)
            if (getSelectedItemCount() > 0) Log.d("Restore selection $selectedPositions", TAG)
        }
    }


    /*---------------*/
    /* FAST SCROLLER */
    /*---------------*/

    /**
     * Displays or Hides the [FastScroller] if previously configured.
     * <br></br>The action is animated.
     *
     * @see .setFastScroller
     */
    fun toggleFastScroller() {
        mFastScrollerDelegate.toggleFastScroller()
    }

    /**
     * @return true if [FastScroller] is configured and shown, false otherwise
     */
    fun isFastScrollerEnabled(): Boolean {
        return mFastScrollerDelegate.isFastScrollerEnabled
    }

    /**
     * @return the current instance of the [FastScroller] object
     */
    fun getFastScroller(): FastScroller? {
        return mFastScrollerDelegate.fastScroller
    }

    /**
     * Sets up the [FastScroller] with automatic fetch of accent color.
     *
     * **IMPORTANT:** Call this method after the adapter is added to the RecyclerView.
     * **NOTE:** If the device has at least Lollipop, the Accent color is fetched, otherwise
     * for previous version, the default value is used.
     *
     * @param fastScroller instance of [FastScroller]
     */
    override fun setFastScroller(fastScroller: FastScroller) {
        if (recyclerViewHasInitialized())
            mFastScrollerDelegate.fastScroller = fastScroller
        else backupFastScroller = fastScroller
    }


    /**
     * @param position the position of the handle
     * @return the value of the item, default value is: position + 1
     */
    override fun onCreateBubbleText(position: Int): String {
        return (position + 1).toString()
    }

    /**
     * Triggered when FastScroller State is changed.
     *
     * @param scrolling true if the user is actively scrolling, false when idle
     */
    override fun onFastScrollerStateChange(scrolling: Boolean) {
        isFastScroll = scrolling
    }

}