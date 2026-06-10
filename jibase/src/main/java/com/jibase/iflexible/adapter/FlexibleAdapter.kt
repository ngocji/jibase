package com.jibase.iflexible.adapter

import android.os.*
import android.view.ViewGroup
import androidx.annotation.CallSuper
import androidx.annotation.IntRange
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.snackbar.Snackbar.Callback.DISMISS_EVENT_MANUAL
import com.jibase.extensions.*
import com.jibase.iflexible.entities.Notification
import com.jibase.iflexible.entities.Notification.Companion.ADD
import com.jibase.iflexible.entities.Notification.Companion.CHANGE
import com.jibase.iflexible.entities.Notification.Companion.MOVE
import com.jibase.iflexible.entities.Notification.Companion.NONE
import com.jibase.iflexible.entities.Notification.Companion.REMOVE
import com.jibase.iflexible.entities.Payload
import com.jibase.iflexible.helpers.FlexibleDiffCallback
import com.jibase.iflexible.helpers.ItemTouchHelperCallback
import com.jibase.iflexible.helpers.StickyHeaderHelper
import com.jibase.iflexible.items.interfaceItems.*
import com.jibase.iflexible.listener.*
import com.jibase.iflexible.viewholder.FlexibleExpandableViewHolder
import com.jibase.iflexible.viewholder.FlexibleViewHolder
import com.jibase.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.ceil
import kotlin.math.max

@Suppress("UNCHECKED_CAST")
open class FlexibleAdapter<T : IFlexible<*>>(
    var listData: MutableList<T> = mutableListOf(),
    hasStateId: Boolean = false
) : AbstractFlexibleAnimatorAdapter(hasStateId), ItemTouchHelperCallback.AdapterCallback {

    companion object {
        private val TAG = "FlexibleAdapter"
        val EXTRA_PARENT = TAG + "_parentSelected"
        val EXTRA_CHILD = TAG + "_childSelected"
        val EXTRA_HEADERS = TAG + "_headersShown"
        val EXTRA_STICKY = TAG + "_stickyHeaders"
        val EXTRA_LEVEL = TAG + "_selectedLevel"
        val EXTRA_FILTER = TAG + "_filter"

        private const val ACTION_UPDATE = 1
        private const val ACTION_FILTER = 2
        private const val ACTION_LOAD_MORE_COMPLETE = 8
        private const val ANIMATE_TO_LIMIT = 1000
        private const val AUTO_SCROLL_DELAY = 150L

        const val IDLE = 0
        const val SINGLE = 1
        const val MULTI = 2
    }

    /* The main container for ALL items */
    private var mTempItems: List<T> = listOf()
    private var mOriginalList: MutableList<T> = mutableListOf()

    /* HashSet, Coroutines and DiffUtil objects, will increase performance in big list */
    private var mHashItems: Set<T>? = null
    private var mNotifications: MutableList<Notification>? = null
    private var mFilterJob: Job? = null
    private var startTimeFilter = 0L
    private var endTimeFiltered = 0L

    /* DiffUtil */
    private var useDiffUtil = true
    private var diffResult: DiffUtil.DiffResult? = null
    private var diffUtilCallback: FlexibleDiffCallback<T>? = null


    /* Deleted items and RestoreList (Undo) */
    private val mRestoreList = mutableListOf<RestoreInfo>()
    private val mUndoPositions = mutableListOf<Int>()
    private var restoreSelection = false
    private var multiRange = false
    private var unlinkOnRemoveHeader = false
    private var permanentDelete = true
    private var adjustSelected = true

    /* Scrollable Headers/Footers items */
    private val mScrollableHeaders = mutableListOf<T>()
    private val mScrollableFooters = mutableListOf<T>()

    /* Section items (with sticky headers) */
    private var headersShown = false
    private var recursive = false
    private var mStickyElevation = 0
    private var mStickyHeaderHelper: StickyHeaderHelper<T>? = null
    private var mStickyContainer: ViewGroup? = null

    /* ViewTypes */
    private val mTypeInstances = mutableMapOf<Int, T>()
    private var autoMap = false


    /* Filter */
    private var mFilterEntity = StringBuilder()
    private var mOldFilterEntity = StringBuilder()

    private var mExpandedFilterFlags: MutableSet<IExpandable<*, *>>? = null
    private var notifyChangeOfUnfilteredItems = true
    private var filtering = false
    private var notifyMoveOfFilteredItems = false
    private var mAnimateToLimit = ANIMATE_TO_LIMIT


    /* Expandable flags */
    private var mMinCollapsibleLevel = 0
    private var mSelectedLevel = -1
    private var scrollOnExpand = false
    private var collapseOnExpand = false
    private var collapseSubLevels = false
    private var childSelected = false
    private var parentSelected = false

    /* Drag&Drop and Swipe helpers */
    private var mItemTouchHelperCallback: ItemTouchHelperCallback? = null
    private var mItemTouchHelper: ItemTouchHelper? = null

    /* EndlessScroll */
    private var mEndlessScrollThreshold = 1
    private var mEndlessTargetCount = 0
    private var mEndlessPageSize = 0
    private var endlessLoading = false
    private var endlessScrollEnabled = false
    private var mTopEndless = false
    private var mProgressItem: T? = null

    /* Listeners */
    var onItemClickListener: OnItemClickListener? = null
    var onItemLongClickListener: OnItemLongClickListener? = null
    var onUpdateListener: OnUpdateListener? = null
    var onFilterListener: OnFilterListener? = null
    var onItemMoveListener: OnItemMoveListener? = null
    var onItemSwipeListener: OnItemSwipeListener? = null
    var onEndlessScrollListener: EndlessScrollListener? = null
    var onDeleteCompleteListener: OnDeleteCompleteListener? = null
    var onStickyHeaderChangeListener: OnStickyHeaderChangeListener? = null
    private val coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)


    init {
        registerAdapterDataObserver(AdapterDataObserver())
    }


    /**
     * Initializes the listener(s) of this Adapter.
     *
     * This method is automatically called from the Constructor.
     *
     * @param listener the object(s) instance(s) of any listener
     * @return this Adapter, so the call can be chained
     * @see removeListener
     */
    @CallSuper
    fun addListener(listener: Any): FlexibleAdapter<T> {
        when (listener) {
            is OnItemClickListener -> {
                Log.d("- OnItemClickClickListener", TAG)
                onItemClickListener = listener
                for (holder in getAllBoundViewHolders()) {
                    holder.contentView.setOnClickListener(holder)
                }
            }

            is OnItemLongClickListener -> {
                Log.d("- OnItemLongClickListener", TAG)
                onItemLongClickListener = listener
                // Restore the event
                for (holder in getAllBoundViewHolders()) {
                    holder.contentView.setOnLongClickListener(holder)
                }
            }

            is OnItemMoveListener -> {
                Log.d("- OnItemMoveListener", TAG)
                onItemMoveListener = listener
            }

            is OnItemSwipeListener -> {
                Log.d("- OnItemSwipeListener", TAG)
                onItemSwipeListener = listener
            }

            is OnDeleteCompleteListener -> {
                Log.d("- OnDeleteCompleteListener", TAG)
                onDeleteCompleteListener = listener
            }

            is OnStickyHeaderChangeListener -> {
                Log.d("- OnStickyHeaderChangeListener", TAG)
                onStickyHeaderChangeListener = listener
            }

            is OnUpdateListener -> {
                Log.d("- OnUpdateListener", TAG)
                onUpdateListener = listener
                listener.onUpdateEmptyView(this, getMainItemCount())
            }

            is OnFilterListener -> {
                Log.d("- OnFilterListener", TAG)
                onFilterListener = listener
            }
        }
        return this
    }


    /**
     * Removes one listener from this Adapter.
     *
     * **Warning:**
     *  * In case of *Click* and *LongClick* events, it will remove also the callback
     * from all bound ViewHolders too. To restore these 2 events on the current bound ViewHolders,
     * call [.addListener] providing the instance of the desired listener.
     *  * To remove a specific listener you have to provide the either the instance or the Class
     * type of the listener, example:
     * removeListener(mUpdateListener);
     * removeListener(FlexibleAdapter.OnItemLongClickListener.class);</pre>
     *
     * @param listener the listener instance or Class type to remove from this Adapter and/or from all bound ViewHolders
     * @return this Adapter, so the call can be chained
     * @see addListener
     */
    @CallSuper
    fun removeListener(listener: Any): FlexibleAdapter<T> {
        when (listener) {
            is OnItemClickListener -> {
                Log.d("- Remove OnItemClickListener", TAG)
                onItemClickListener = null
                for (holder in getAllBoundViewHolders()) {
                    holder.contentView.setOnClickListener(null)
                }
            }

            is OnItemLongClickListener -> {
                Log.d("- Remove OnItemLongClickListener", TAG)
                onItemLongClickListener = null
                // Restore the event
                for (holder in getAllBoundViewHolders()) {
                    holder.contentView.setOnLongClickListener(null)
                }
            }

            is OnItemMoveListener -> {
                Log.d("- Remove OnItemMoveListener", TAG)
                onItemMoveListener = null
            }

            is OnItemSwipeListener -> {
                Log.d("- Remove OnItemSwipeListener", TAG)
                onItemSwipeListener = null
            }

            is OnDeleteCompleteListener -> {
                Log.d("- Remove OnDeleteCompleteListener", TAG)
                onDeleteCompleteListener = null
            }

            is OnStickyHeaderChangeListener -> {
                Log.d("- Remove OnStickyHeaderChangeListener", TAG)
                onStickyHeaderChangeListener = null
            }

            is OnUpdateListener -> {
                Log.d("- Remove OnUpdateListener", TAG)
                onUpdateListener = null
            }

            is OnFilterListener -> {
                Log.d("- Remove OnFilterListener", TAG)
                onFilterListener = null
            }
        }
        return this
    }

    /**
     *
     * Attaches the `StickyHeaderHelper` to the RecyclerView if necessary.
     *
     */
    @CallSuper
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        Log.d("Attached Adapter to RecyclerView", TAG)
        if (headersShown && areHeadersSticky()) {
            mStickyHeaderHelper?.attachToRecyclerView(mRecyclerView)
        }
    }

    /**
     *
     * Detaches the `StickyHeaderHelper` from the RecyclerView if necessary.
     *
     */
    @CallSuper
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        if (areHeadersSticky()) {
            mStickyHeaderHelper?.detachFromRecyclerView()
            mStickyHeaderHelper = null
        }
        release()
        super.onDetachedFromRecyclerView(recyclerView)
        Log.d("Detached Adapter from RecyclerView", TAG)
    }

    fun release() {
        mFilterJob?.cancel()
        coroutineScope.coroutineContext.cancelChildren()
    }

    /**
     * Maps and expands items that are initially configured to be shown as expanded.
     *
     * This method should be called during the creation of the Activity/Fragment, useful also after a screen rotation.
     *
     * @return this Adapter, so the call can be chained
     */
    //todo review expand at start up
    fun setExpandItemsAtStartUp(): FlexibleAdapter<T> {
        var position = 0
        setScrollAnimate(true)
        multiRange = true
        while (position < itemCount) {
            val item = getItem(position)
            if (!headersShown && isHeader(item) && false == item?.isHidden()) {
                headersShown = true
            }
            if (isExpanded(item)) {
                position += expand(position, init = true)
                continue
            } else {
                position++
            }
        }
        multiRange = false
        setScrollAnimate(false)
        return this
    }

    /**
     * Checks if the current item has the property `enabled = true`.
     *
     * When an item is disabled, user cannot interact with it.
     *
     * @param position the current position of the item to check
     * @return true if the item property *enabled* is set true, false otherwise
     */
    fun isItemEnabled(position: Int): Boolean {
        return getItem(position)?.isEnabled() ?: false
    }


    /**
     * Check if the position has valid in the preview data
     * @param position the position of item check
     * @return true if the position in preview data
     */
    fun hasPosition(position: Int): Boolean {
        return listData hasPosition position
    }

    /*------------------------------*/
    /* SELECTION METHODS OVERRIDDEN */
    /*------------------------------*/


    fun setMode(mode: Int): FlexibleAdapter<T> {
        this.mode = mode
        return this
    }


    override fun isSelectable(position: Int): Boolean {
        return getItem(position)?.isSelectable() ?: false
    }

    /**
     * *
     * @param position position of the item to toggle the selection status for.
     */
    override fun toggleSelection(@IntRange(from = 0) position: Int) {
        getItem(position)?.also { item ->
            // Allow selection only for enableSelect items
            if (item.isSelectable()) {
                val parent = getExpandableOf(item)
                val hasParent = parent != null
                if ((isExpandableItem(item) || !hasParent) && !childSelected) {
                    parentSelected = true
                    if (hasParent) mSelectedLevel = parent?.getExpansionLevel() ?: 0
                    super.toggleSelection(position)
                } else if (parent != null && (mSelectedLevel == -1 || !parentSelected && mSelectedLevel == parent.getExpansionLevel() + 1)) {
                    childSelected = true
                    mSelectedLevel = parent.getExpansionLevel() + 1
                    super.toggleSelection(position)
                }
            }
        }

        // Reset flags if necessary, just to be sure
        if (super.getSelectedItemCount() == 0) {
            mSelectedLevel = -1
            childSelected = false
            parentSelected = false
        }
    }

    /**
     * Helper to automatically select all the items of the viewType equal to the viewType of
     * the first selected item.
     *
     * Examples:
     *  * if user initially selects an expandable of type A, then only expandable items of
     * type A can be selected.
     *  * if user initially selects a non-expandable of type B, then only items of type B
     * can be selected.
     *  * The developer can override this behaviour by passing a list of viewTypes for which
     * he wants to force the selection.
     *
     * @param viewTypes All the desired viewTypes to be selected, providing no view types, will
     * automatically select all the viewTypes of the first item user has selected
     */
    override fun selectAll(vararg viewTypes: Int) {
        if (getSelectedItemCount() > 0 && viewTypes.isEmpty()) {
            super.selectAll(getItemViewType(getSelectedPositions().first())) //Priority on the first item
        } else {
            super.selectAll(*viewTypes) //Force the selection for the viewTypes passed
        }
    }


    fun getSelectedItems(): List<T> {
        return getSelectedPositionsAsSet().mapNotNull { getItem(it) }
    }


    @CallSuper
    override fun clearSelection() {
        childSelected = false
        parentSelected = false
        super.clearSelection()
    }

    /**
     * @return true if a parent is selected
     */
    fun isAnyParentSelected(): Boolean {
        return parentSelected
    }

    /**
     * @return true if any child of any parent is selected, false otherwise
     */
    fun isAnyChildSelected(): Boolean {
        return childSelected
    }


    /*--------------*/
    /* MAIN METHODS */
    /*--------------*/


    /**
     * Convenience method of [.updateDataSet] (You should read the comments
     * of this method).
     *
     * In this call, changes will NOT be animated: **Warning!**
     * [.notifyDataSetChanged] will be invoked.
     *
     * @param items the new data set
     * @see updateDataSet
     */
    //todo review update
    @CallSuper
    fun updateDataSet(items: List<T>, animate: Boolean = false) {
        mOriginalList = mutableListOf() // Reset original list from filter
        if (animate) {
            mFilterJob?.cancel()
            mFilterJob = filterAsync(ACTION_UPDATE, items)
        } else {
            // Copy of the original list
            val newItems = items.toMutableList()
            prepareItemsForUpdate(newItems)
            listData = newItems
            // Execute instant reset on init
            Log.d("updateDataSet with notifyDataSetChanged!")
            notifyDataSetChanged()
            onPostUpdate()
        }
    }

    fun setData(items: List<T>) {
        listData = items.toMutableList()
    }

    /**
     * Returns the object of the generic type **T**.
     *
     * This method cannot be overridden since the entire library relies on it.
     *
     * @param position the position of the item in the list
     * @return The **T** object for the position provided or null if item not found
     */
    fun getItem(position: Int): T? {
        return listData.getOrNull(position)
    }

    /**
     * Returns the object of specific type **S**.
     *
     * @param position the position of the item in the list
     * @param clazz    the class type expected
     * @return The **S** object for the position provided or null if item not found
     */
    inline fun <reified S : T> getItemAsClass(position: Int): S? {
        return getItem(position) as? S
    }

    /**
     * This method is mostly used by the adapter if items have stableIds.
     *
     * @param position the position of the current item
     * @return Hashcode of the item at the specific position
     */
    override fun getItemId(position: Int): Long {
        return getItem(position)?.hashCode()?.toLong() ?: RecyclerView.NO_ID
    }


    /**
     * Returns the total number of items in the data set held by the adapter (headers and footers
     * INCLUDED). Use [.getMainItemCount] with `false` as parameter to retrieve
     * only real items excluding headers and footers.
     *
     * **Note:** This method cannot be overridden since the selection and the internal
     * methods rely on it.
     *
     * @return the total number of items (headers and footers INCLUDED) held by the adapter
     * @see getMainItemCount
     * @see getItemCountOfTypes
     * @see isEmpty
     */
    override fun getItemCount(): Int {
        return listData.size
    }

    /**
     * Returns the total number of items in the data set held by the adapter.
     *
     *  * Provide `true` (default behavior) to count all items, same result of [.getItemCount].
     *  * Provide `false` to count only main items (headers and footers are EXCLUDED).
     *
     * **Note:** This method cannot be overridden since internal methods rely on it.
     *
     * @return the total number of items held by the adapter, with or without headers and footers,
     * depending by the provided parameter
     * @see getItemCount
     * @see getItemCountOfTypes
     */
    fun getMainItemCount(): Int {
        return if (hasFilter()) itemCount else itemCount - mScrollableHeaders.size - mScrollableFooters.size
    }


    /**
     * Provides the number of items currently displayed of one or more certain types.
     *
     * @param viewTypes the viewTypes to count
     * @return number of the viewTypes counted
     * @see getItemCount
     * @see getMainItemCount
     * @see isEmpty
    =     */
    fun getItemCountOfTypes(vararg viewTypes: Int): Int {
        var count = 0
        for (i in 0 until itemCount) {
            if (viewTypes.contains(getItemViewType(i)))
                count++
        }
        return count
    }

    /**
     * Gets an unmodifiable view of the internal list of items.
     *
     * @return an unmodifiable view of the current adapter list
     */
    fun getCurrentItems(): List<T> {
        return Collections.unmodifiableList(listData)
    }

    /**
     * You can override this method to define your own concept of "Empty". This method is never
     * called internally.
     *
     * @return true if the list is empty, false otherwise
     * @see getItemCount
     * @see getItemCountOfTypes
     */
    fun isEmpty(): Boolean {
        return itemCount == 0
    }

    /**
     * Retrieves the global position of the item in the Adapter list.
     * If no scrollable Headers are added, the global position coincides with the cardinal position.
     *
     * This method cannot be overridden since the entire library relies on it.
     *
     * @param item the item for which the position needs to be found
     * @return the global position in the Adapter if found, -1 otherwise
     * @see getSameTypePositionOf
     */
    fun getGlobalPositionOf(item: T?): Int {
        return listData.indexOf(item)
    }

    /**
     * Retrieves the position of the Main item in the Adapter list excluding the scrollable Headers.
     * If no scrollable Headers are added, the cardinal position coincides with the global position.
     *
     * **Note:**
     * - This method is NOT suitable to call when managing items: ALL insert, remove, move and
     * swap operations, should done with global position [.getGlobalPositionOf].
     * - This method cannot be overridden.
     *
     * @param item the item for which the position needs to be found
     * @return the position in the Adapter excluding the Scrollable Headers, -1 otherwise
     * @see getSameTypePositionOf
     */
    fun getCardinalPositionOf(item: T): Int {
        var position = getGlobalPositionOf(item)
        if (position > mScrollableHeaders.size) position -= mScrollableHeaders.size
        return position
    }

    /**
     * Retrieves the position of any item in the Adapter <u>counting</u> only the items of the
     * same view type of the provided item and <u>excluding</u> all the others view types.
     *
     * **Tip:** You can identify the number of the section (you need to add +1) of any
     * headers OR to retrieve the position of an item as it were the only view type visible in
     * the Adapter.
     *
     * @param item the item for which the position needs to be found
     * @return the position in the Adapter counting only the items of the same type, -1 otherwise
     * @see getSubPositionOf
     */
    fun getSameTypePositionOf(item: T): Int {
        var position = -1
        for (current in listData) {
            if (current.getItemViewType() == item.getItemViewType()) {
                position++
                if (current == item) break
            }
        }
        return position
    }

    /**
     * This method is never called internally.
     *
     * @param item the item to find
     * @return true if the provided item is currently displayed, false otherwise
     */
    operator fun contains(item: T): Boolean {
        return listData.contains(item)
    }

    /**
     * New method to extract the new position where the item should lay.
     *
     * **Note: **The `Comparator` object should be customized to support <u>all</u>
     * types of items this Adapter is managing or a `ClassCastException` will be raised.
     * If the `Comparator` is `null` the returned position is 0 (first position).
     *
     * @param item       the item to evaluate the insertion
     * @param comparator the Comparator object with the logic to sort the list
     * @return the position resulted from sorting with the provided Comparator
     */
    fun calculatePositionFor(item: T, comparator: Comparator<T>): Int {
        // There's nothing to compare

        // Header is visible
        if (item is ISectionable<*, *>) {
            item.getHeader()?.also { header ->
                if (header.isHidden()) {
                    val sortedList = getSectionItems(header).toMutableList()
                    sortedList.add(item)
                    Collections.sort(sortedList, comparator as Comparator<in ISectionable<*, *>>)
                    val itemPosition = getGlobalPositionOf(item)
                    val headerPosition = getGlobalPositionOf(header as T)
                    // #143 - calculatePositionFor() missing a +1 when addItem (fixed by condition: itemPosition != -1)
                    // fix represents the situation when item is before the target position (used in moveItem)
                    val fix = if (itemPosition != -1 && itemPosition < headerPosition) 0 else 1
                    val result = headerPosition + sortedList.indexOf(item) + fix
                    Log.d(
                        "Calculated finalPosition=$result sectionPosition=$headerPosition relativePosition=${
                            sortedList.indexOf(
                                item
                            )
                        } fix=$fix", TAG
                    )
                    return result
                }
            }
        }
        // All other cases
        val sortedList = ArrayList<T>(listData)
        if (!sortedList.contains(item)) sortedList.add(item)
        Collections.sort(sortedList, comparator)
        Log.d("Calculated position ${max(0, sortedList.indexOf(item))} for item=$item", TAG)
        return max(0, sortedList.indexOf(item))
    }


    /*------------------------------------*/
    /* SCROLLABLE HEADERS/FOOTERS METHODS */
    /*------------------------------------*/

    /**
     * @return unmodifiable list of Scrollable Headers currently held by the Adapter
     * @see addScrollableHeader
     * @see addScrollableHeaderWithDelay
     */
    fun getScrollableHeaders(): List<T> {
        return Collections.unmodifiableList(mScrollableHeaders)
    }

    /**
     * @return unmodifiable list of Scrollable Footers currently held by the Adapter
     * @see addScrollableFooter
     * @see addScrollableFooterWithDelay
     */
    fun getScrollableFooters(): List<T> {
        return Collections.unmodifiableList(mScrollableFooters)
    }


    override fun isScrollableHeaderOrFooter(position: Int): Boolean {
        val item = getItem(position)
        return if (item != null) isScrollableHeaderOrFooter(item) else false
    }

    /**
     * Checks if at the provided item is a Header or Footer.
     *
     * @param item the item to check
     * @return true if it's a scrollable item
     */
    fun isScrollableHeaderOrFooter(item: T): Boolean {
        return mScrollableHeaders.contains(item) || mScrollableFooters.contains(item)
    }


    /**
     * Adds a Scrollable Header.
     *
     * **Scrollable Headers** have the following characteristic:
     *
     *  * lay always before any main item.
     *  * cannot be enableSelect nor draggable.
     *  * cannot be inserted twice, but many can be inserted.
     *  * any new header will be inserted before the existent.
     *  * can be of any type so they can be bound at runtime with any data inside.
     *  * won't be filtered because they won't be part of the main list, but added separately
     * at the initialization phase
     *  * can be added and removed with certain delay.
     *
     *
     * @param headerItem the header item to be added
     * @return true if the header has been successfully added, false if the header already exists
     * @see getScrollableHeaders
     * @see addScrollableHeaderWithDelay
     */
    fun addScrollableHeader(headerItem: T): Boolean {
        Log.d("Add scrollable header $headerItem", TAG)
        return if (!mScrollableHeaders.contains(headerItem)) {
            headerItem.setSelectable(false)
            headerItem.setDraggable(false)
            val progressFix =
                if (isEndlessProgressItemOf(headerItem)) mScrollableHeaders.size else 0
            mScrollableHeaders.add(headerItem)
            setScrollAnimate(true) // Headers will scroll animate
            performInsert(progressFix, listOf(headerItem), true)
            setScrollAnimate(false)
            true
        } else {
            Log.d("Scrollable header $headerItem already added", TAG)
            false
        }
    }

    /**
     * Adds a Scrollable Footer.
     *
     * **Scrollable Footers** have the following characteristic:
     *
     *  * lay always after any main item.
     *  * cannot be enableSelect nor draggable.
     *  * cannot be inserted twice, but many can be inserted.
     *  * cannot scroll animate, when inserted for the first time.
     *  * any new footer will be inserted after the existent.
     *  * can be of any type so they can be bound at runtime with any data inside.
     *  * won't be filtered because they won't be part of the main list, but added separately
     * at the initialization phase
     *  * can be added and removed with certain delay.
     *  * endless `progressItem` is handled as a Scrollable Footer, but it will be always
     * displayed between the main items and the others footers.
     *
     *
     * @param footerItem the footer item to be added
     * @return true if the footer has been successfully added, false if the footer already exists
     * @see getScrollableFooters
     * @see addScrollableFooterWithDelay
     */
    fun addScrollableFooter(footerItem: T): Boolean {
        return if (!mScrollableFooters.contains(footerItem)) {
            Log.d("Add scrollable footer $footerItem", TAG)
            footerItem.setSelectable(false)
            footerItem.setDraggable(false)
            val progressFix =
                if (isEndlessProgressItemOf(footerItem)) mScrollableFooters.size else 0
            //Prevent wrong position after a possible updateDataSet
            if (progressFix > 0 && mScrollableFooters.size > 0) {
                mScrollableFooters.add(0, footerItem)
            } else {
                mScrollableFooters.add(footerItem)
            }
            performInsert(itemCount - progressFix, listOf(footerItem), true)
            true
        } else {
            Log.d("Scrollable footer $footerItem already added", TAG)
            false
        }
    }

    /**
     * Removes the provided Scrollable Header.
     *
     * @param headerItem the header to remove
     * @see removeScrollableHeaderWithDelay
     * @see removeAllScrollableHeaders
     */
    fun removeScrollableHeader(headerItem: T) {
        if (mScrollableHeaders.remove(headerItem)) {
            Log.d("Remove scrollable header $headerItem", TAG)
            performRemove(headerItem, true)
        }
    }

    /**
     * Removes the provided Scrollable Footer.
     *
     * @param footerItem the footer to remove
     * @see removeScrollableFooterWithDelay
     * @see removeAllScrollableFooters
     */
    fun removeScrollableFooter(footerItem: T) {
        if (mScrollableFooters.remove(footerItem)) {
            Log.d("Remove scrollable footer $footerItem", TAG)
            performRemove(footerItem, true)
        }
    }

    /**
     * Removes all Scrollable Headers at once.
     *
     * @see removeScrollableHeader
     * @see removeScrollableHeaderWithDelay
     */
    fun removeAllScrollableHeaders() {
        if (mScrollableHeaders.isNotEmpty()) {
            Log.d("Remove all scrollable headers", TAG)
            listData.removeAll(mScrollableHeaders)
            notifyItemRangeRemoved(0, mScrollableHeaders.size)
            mScrollableHeaders.clear()
        }
    }

    /**
     * Removes all Scrollable Footers at once.
     *
     * @see removeScrollableFooter
     * @see removeScrollableFooterWithDelay
     */
    fun removeAllScrollableFooters() {
        if (mScrollableFooters.isNotEmpty()) {
            Log.d("Remove all scrollable footers", TAG)
            listData.removeAll(mScrollableFooters)
            notifyItemRangeRemoved(itemCount - mScrollableFooters.size, mScrollableFooters.size)
            mScrollableFooters.clear()
        }
    }

    /**
     * Same as [.addScrollableHeader] but with a delay and the possibility to
     * scroll to it.
     *
     * @param headerItem       the header item to be added
     * @param delay            the delay in ms
     * @param scrollToPosition true to scroll to the header item position once it has been added
     * @see addScrollableHeader
     */
    fun addScrollableHeaderWithDelay(
        headerItem: T, @IntRange(from = 0) delay: Long,
        scrollToPosition: Boolean
    ) {
        Log.d("Enqueued adding scrollable header ($delay ms) $headerItem", TAG)
        coroutineScope.launch(Dispatchers.Main) {
            delay(delay)
            if (addScrollableHeader(headerItem) && scrollToPosition) {
                smoothScrollToPosition(getGlobalPositionOf(headerItem))
            }
        }
    }

    /**
     * Same as [.addScrollableFooter] but with a delay and the possibility to
     * scroll to it.
     *
     * @param footerItem       the footer item to be added
     * @param delay            the delay in ms
     * @param scrollToPosition true to scroll to the footer item position once it has been added
     * @see addScrollableFooter
     */
    fun addScrollableFooterWithDelay(
        footerItem: T, @IntRange(from = 0) delay: Long,
        scrollToPosition: Boolean
    ) {
        Log.d("Enqueued adding scrollable footer ($delay ms) $footerItem", TAG)
        coroutineScope.launch(Dispatchers.Main) {
            delay(delay)
            if (addScrollableFooter(footerItem) && scrollToPosition) {
                smoothScrollToPosition(getGlobalPositionOf(footerItem))
            }
        }
    }

    /**
     * Same as [.removeScrollableHeader] but with a delay.
     *
     * @param headerItem the header item to be removed
     * @param delay      the delay in ms
     * @see removeScrollableHeader
     * @see removeAllScrollableHeaders
     */
    fun removeScrollableHeaderWithDelay(headerItem: T, @IntRange(from = 0) delay: Long) {
        Log.d("Enqueued removing scrollable header ($delay ms) $headerItem", TAG)
        coroutineScope.launch(Dispatchers.Main) {
            delay(delay)
            removeScrollableHeader(headerItem)
        }
    }

    /**
     * Same as [.removeScrollableFooter] but with a delay.
     *
     * @param footerItem the footer item to be removed
     * @param delay      the delay in ms
     * @see removeScrollableFooter
     * @see removeAllScrollableFooters
     */
    fun removeScrollableFooterWithDelay(footerItem: T, @IntRange(from = 0) delay: Long) {
        Log.d("Enqueued removing scrollable footer ($delay ms) $footerItem", TAG)
        coroutineScope.launch(Dispatchers.Main) {
            delay(delay)
            removeScrollableFooter(footerItem)
        }
    }

    /**
     * Helper method to restore the scrollable headers/footers along with the main items.
     * After the update and the filter operations.
     */
    private fun restoreScrollableHeadersAndFooters(items: MutableList<T>) {
        if (items.isNotEmpty()) {
            items.addAll(0, mScrollableHeaders)
        } else {
            items.addAll(mScrollableHeaders)
        }
        items.addAll(mScrollableFooters)
    }

    /*--------------------------*/
    /* HEADERS/SECTIONS METHODS */
    /*--------------------------*/

    /**
     * Setting to automatically unlink the deleted header from items having that header linked.
     *
     * Default value is `false`.
     *
     * @param unlinkOnRemoveHeader true to unlink the deleted header from items having that header
     * linked, false otherwise
     * @return this Adapter, so the call can be chained
     */
    fun setUnlinkAllItemsOnRemoveHeaders(unlinkOnRemoveHeader: Boolean): FlexibleAdapter<T> {
        Log.d("Set unlinkOnRemoveHeader= $unlinkOnRemoveHeader", TAG)
        this.unlinkOnRemoveHeader = unlinkOnRemoveHeader
        return this
    }

    /**
     * Retrieves all the header items.
     *
     * @return non-null list with all the header items
     */
    fun getHeaderItems(): List<IHeader<*>> {
        return listData.filter { isHeader(it) } as List<IHeader<*>>
    }

    /**
     * @param item the item to check
     * @return true if the item is an instance of [IHeader] interface, false otherwise
     */
    fun <T> isHeader(item: T?): Boolean {
        return item != null && item is IHeader<*>
    }

    /**
     * @param position the item to check
     * @return true if the item is an instance of [IHeader] interface, false otherwise
     */
    fun isHeader(@IntRange(from = 0) position: Int): Boolean {
        if (listData hasPosition position) {
            return isHeader(listData[position])
        }
        return false
    }

    /**
     * Helper method to check if an item holds a header.
     *
     * @param item the identified item
     * @return true if the item holds a header, false otherwise
     */
    fun hasHeader(item: T): Boolean {
        return getHeaderOf(item) != null
    }

    /**
     * Checks if the item has a header and that header is the same of the provided one.
     *
     * @param item   the item supposing having the header
     * @param header the header to compare
     * @return true if the item has a header and it is the same of the provided one, false otherwise
     */
    fun hasSameHeader(item: T, header: IHeader<*>?): Boolean {
        val current = getHeaderOf(item)
        return current != null && header != null && current == header
    }

    /**
     * Retrieves the header of the provided `ISectionable` item.
     *
     * @param item the ISectionable item holding a header
     * @return the header of the passed Sectionable, null otherwise
     */
    fun getHeaderOf(item: T): IHeader<*>? {
        return if (item is ISectionable<*, *>) {
            (item as ISectionable<*, *>).getHeader()
        } else null
    }

    /**
     * Retrieves the [IHeader] item of any specified position.
     *
     * @param position the item position
     * @return the IHeader item linked to the specified item position
     */
    fun getSectionHeader(@IntRange(from = 0) position: Int): IHeader<*>? {
        // Headers are not visible nor sticky
        if (!headersShown) return null
        // When headers are visible and sticky, get the previous header
        for (i in position downTo 0) {
            val item = getItem(i)
            if (isHeader(item)) return item as? IHeader<*>
        }
        return null
    }

    /**
     * Provides all the items that belongs to the section represented by the provided header.
     *
     * @param header the `IHeader` item that represents the section
     * @return NonNull list of all items in the provided section
     */
    fun getSectionItems(header: IHeader<*>): List<ISectionable<*, *>> {
        val sectionItems = mutableListOf<ISectionable<*, *>>()
        var startPosition = getGlobalPositionOf(header as T)
        var item = getItem(++startPosition)
        if (item != null) {
            while (hasSameHeader(item as T, header)) {
                (item as? ISectionable<*, *>)?.also {
                    sectionItems.add(it)
                }
                item = getItem(++startPosition)
            }
        }
        return sectionItems
    }

    /**
     * Provides all the item positions that belongs to the section represented by the provided
     * header.
     *
     * @param header the `IHeader` item that represents the section
     * @return NonNull list of all item positions in the provided section
     */
    fun getSectionItemPositions(header: IHeader<*>): List<Int> {
        val sectionItemPositions = mutableListOf<Int>()
        var startPosition = getGlobalPositionOf(header as T)
        var item = getItem(++startPosition)
        if (item != null) {
            while (hasSameHeader(item as T, header)) {
                sectionItemPositions.add(startPosition)
                item = getItem(++startPosition)
            }
        }
        return sectionItemPositions
    }

    /**
     * Evaluates if Adapter has headers shown.
     *
     * @return true if all headers are currently displayed, false otherwise
     */
    fun areHeadersShown(): Boolean {
        return headersShown
    }

    /**
     * Evaluates if Adapter can actually display sticky headers on the top.
     *
     * @return true if headers can be sticky, false if headers are scrolled together with all items
     */
    fun areHeadersSticky(): Boolean {
        return mStickyHeaderHelper != null
    }

    /**
     * Returns the current position of the sticky header.
     *
     * @return the current sticky header position, -1 if no header is sticky
     */
    fun getStickyPosition(): Int {
        return if (areHeadersSticky()) mStickyHeaderHelper?.getStickyPosition() ?: -1 else -1
    }

    /**
     * Ensures the current sticky header view is correctly displayed in the sticky layout.
     *
     */
    fun ensureHeaderParent() {
        if (areHeadersSticky()) mStickyHeaderHelper?.ensureHeaderParent()
    }

    /**
     * Enables/Disables the sticky header feature with default sticky layout container.
     *
     * **Note:**
     *
     *  * You should consider to display headers with [.setDisplayHeadersAtStartUp]:
     * Feature can enabled/disabled freely, but if headers are hidden nothing will happen.
     *  * Only in case of "Sticky Header" items you must <u>provide</u> `true` to the
     * constructor: [FlexibleViewHolder.FlexibleViewHolder].
     *  * Optionally, you can set a custom sticky layout container that must be already
     * inflated
     * Check [.setStickyHeaders].
     *  * Optionally, you can set a layout elevation: Header item elevation is used first,
     * if not set, default elevation of `21f` pixel is used.
     *  * Sticky headers are clickable as any views, but cannot be dragged nor swiped.
     *  * Content and linkage are automatically updated.
     *  * Sticky layout container is *fade-in* and *fade-out* animated when feature
     * is respectively enabled and disabled.
     *  * Sticky container can be elevated only if the header item layout has elevation (Do not
     * exaggerate with elevation).
     *
     *
     * **Important!** In order to display the Refresh circle AND the FastScroller on the Top of
     * the sticky container, the RecyclerView must be wrapped with a `FrameLayout` as following:
     *
     * <FrameLayout
     * android:layout_width="match_parent"
     * android:layout_height="match_parent">
     *
     * <android.support.v7.widget.RecyclerView
     * android:id="@+id/recycler_view"
     * android:layout_width="match_parent"
     * android:layout_height="match_parent">
     *
     * </FrameLayout>
    </pre> *
     *
     * @param sticky true to initialize sticky headers with default container, false to disable them
     * @return this Adapter, so the call can be chained
     * @throws IllegalStateException if this Adapter was not attached to the RecyclerView
     * @see setStickyHeaders
     * @see setDisplayHeadersAtStartUp
     * @see setStickyHeaderElevation
     */
    fun setStickyHeaders(sticky: Boolean): FlexibleAdapter<T> {
        return setStickyHeaders(sticky, mStickyContainer)
    }

    /**
     * Enables/Disables the sticky header feature with a custom sticky layout container.
     *
     * **Important:** Read the javaDoc of the overloaded method [.setStickyHeaders].
     *
     * @param sticky          true to initialize sticky headers, false to disable them
     * @param stickyContainer user defined and already inflated sticky layout that will
     * hold the sticky header itemViews
     * @return this Adapter, so the call can be chained
     * @throws IllegalStateException if this Adapter was not attached to the RecyclerView
     * @see setStickyHeaders
     * @see setDisplayHeadersAtStartUp
     * @see setStickyHeaderElevation
     */
    fun setStickyHeaders(sticky: Boolean, stickyContainer: ViewGroup?): FlexibleAdapter<T> {
        Log.d(
            "Set stickyHeaders=$sticky (using Coroutines)${if (stickyContainer != null) " with user defined Sticky Container" else ""}",
            TAG
        )

        // With user defined container
        mStickyContainer = stickyContainer

        // Run using Coroutines to be sure about the RecyclerView initialization
        coroutineScope.launch(Dispatchers.Main) {
            // Enable or Disable the sticky headers layout
            if (sticky) {
                if (mStickyHeaderHelper == null) {
                    mStickyHeaderHelper = StickyHeaderHelper(
                        this@FlexibleAdapter,
                        onStickyHeaderChangeListener, mStickyContainer
                    )
                    mStickyHeaderHelper?.attachToRecyclerView(mRecyclerView)
                    Log.d("Sticky headers enabled", TAG)
                }
            } else if (areHeadersSticky()) {
                mStickyHeaderHelper?.detachFromRecyclerView()
                mStickyHeaderHelper = null
                Log.d("Sticky headers disabled", TAG)
            }
        }
        return this
    }

    /**
     * Gets the layout in dpi elevation for sticky header.
     *
     * **Note:** This setting is ignored if the header item has already an elevation. The
     * header elevation overrides this setting.
     *
     * @return the elevation in pixel
     * @see setStickyHeaderElevation
     */
    fun getStickyHeaderElevation(): Int {
        return mStickyElevation
    }

    /**
     * Gets the sticky header helper.
     *
     * @return the StickyHeaderHelper<*>
     * @see setStickyHeaders
     */
    fun getStickyHeaderHelper(): StickyHeaderHelper<*>? {
        return mStickyHeaderHelper
    }

    /**
     * Sets the elevation in dpi for the sticky header layout.
     *
     * **Note:** This setting is ignored if the header item has already an elevation. The
     * header elevation overrides this setting.
     * Default value is 0 dpi.
     *
     * @param stickyElevation the elevation in dpi
     * @return this Adapter, so the call can be chained
     * @see getStickyHeaderElevation
     */
    fun setStickyHeaderElevation(@IntRange(from = 0) stickyElevation: Int): FlexibleAdapter<T> {
        mStickyElevation = stickyElevation
        return this
    }

    /**
     * Sets if all headers should be shown at startup by the Adapter automatically.
     *
     * If called, this method won't trigger `notifyItemInserted()` and scrolling
     * animations are instead performed if the header item was configured with animation:
     * <u>Headers will be loaded/bound along with others items.</u>
     * **Note:** Headers can only be shown or hidden all together.
     *
     * Default value is `false` (headers are <u>not</u> shown at startup).
     *
     * @param displayHeaders true to display headers, false to keep them hidden
     * @return this Adapter, so the call can be chained
     * @see showAllHeaders
     * @see setAnimationOnForwardScrolling
     */
    fun setDisplayHeadersAtStartUp(displayHeaders: Boolean): FlexibleAdapter<T> {
        if (!headersShown && displayHeaders) {
            showAllHeaders(true)
        }
        return this
    }


    /**
     * Manually change the flag to indicate that headers are inserted(or not) in the main list by the user
     * and the Adapter doesn't(does) have to automatically insert them.
     *
     * Default value is `false` (headers are <u>not</u> shown at startup).
     *
     * @param headersShown true, if headers are already shown
     * @return this Adapter, so the call can be chained
     */
    fun setHeadersShown(headersShown: Boolean): FlexibleAdapter<T> {
        this.headersShown = headersShown
        return this
    }

    /**
     * Shows all headers in the RecyclerView at their linked position.
     *
     * If called at startup, this method will trigger `notifyItemInserted` for a
     * different loading effect: <u>Headers will be inserted after the items!</u>
     * **Note:** Headers can only be shown or hidden all together.
     *
     * @return this Adapter, so the call can be chained
     * @see hideAllHeaders
     * @see setDisplayHeadersAtStartUp
     */
    fun showAllHeaders(init: Boolean = false): FlexibleAdapter<T> {
        if (init) {
            Log.d("showAllHeaders at startup", TAG)
            // No notifyItemInserted!
            showAllHeadersWithReset(true)
        } else {
            Log.d("showAllHeaders with insert notification (using Coroutines)", TAG)
            // Using Coroutines, let's notifyItemInserted!
            coroutineScope.launch(Dispatchers.Main) {
                // #144 - Check if headers are already shown, discard the call to not duplicate headers
                if (headersShown) {
                    Log.d(
                        "Double call detected! Headers already shown OR the method showAllHeaders() was already called!",
                        TAG
                    )
                    return@launch
                }
                showAllHeadersWithReset(false)
                // #142 - At startup, when insert notifications are performed to show headers
                // for the first time. Header item is not visible at position 0: it has to be
                // displayed by scrolling to it. This resolves the first item below sticky
                // header when enabled as well.
                val firstVisibleItem =
                    getFlexibleLayoutManager().findFirstCompletelyVisibleItemPosition()
                if (firstVisibleItem == 0 && isHeader(getItem(0)) && !isHeader(getItem(1))) {
                    mRecyclerView.scrollToPosition(0)
                }
            }
        }
        return this
    }

    /**
     * @param init true to skip the call to notifyItemInserted, false otherwise
     */
    private fun showAllHeadersWithReset(init: Boolean) {
        var position = 0
        var sameHeader: IHeader<*>? = null
        while (position < itemCount - mScrollableFooters.size) {
            getItem(position)?.also {
                // Reset hidden status! Necessary after the filter and the update
                val header = getHeaderOf(it)
                if (header != null && header != sameHeader && !isExpandableItem(header as? T)) {
                    sameHeader = header
                    header.setHidden(true)
                }
                if (showHeaderOf(position, it, init)) position++ //It's the same element, skip it
            }
            position++
        }
        headersShown = true
    }


    /**
     * Internal method to show/add a header in the internal list.
     *
     * @param position the position where the header will be displayed
     * @param item     the item that holds the header
     * @param init     for silent initialization: skip notifyItemInserted
     */
    private fun showHeaderOf(position: Int, item: T, init: Boolean): Boolean {
        // Take the header
        val header = getHeaderOf(item)
        // Check header existence
        if (header == null || getPendingRemovedItem(item) != null) return false
        if (header.isHidden()) {
            Log.d("Showing header position=$position header=$header", TAG)
            header.setHidden(false)
            // Insert header, but skip notifyItemInserted when init=true!
            // We are adding headers to the provided list at startup (no need to notify)
            performInsert(position, listOf(header as T), !init)
            return true
        }
        return false
    }

    /**
     * Hides all headers from the RecyclerView.
     *
     * Headers can be shown or hidden all together.
     *
     * @see showAllHeaders
     * @see setDisplayHeadersAtStartUp
     */
    fun hideAllHeaders() {
        coroutineScope.launch(Dispatchers.Main) {
            multiRange = true
            // Hide linked headers between Scrollable Headers and Footers
            var position = itemCount - mScrollableFooters.size - 1
            while (position >= max(0, mScrollableHeaders.size - 1)) {
                val item = getItem(position)
                if (isHeader(item)) hideHeader(position, item as? IHeader<*>)
                position--
            }
            headersShown = false
            // Clear the header currently sticky
            if (areHeadersSticky()) {
                mStickyHeaderHelper?.clearHeaderWithAnimation()
            }
            // setStickyHeaders(false);
            multiRange = false
        }
    }

    /**
     * Internal method to hide/remove a header from the internal list.
     *
     * @param item the item that holds the header
     */
    private fun hideHeaderOf(item: T) {
        val header = getHeaderOf(item)
        // Check header existence
        if (header != null && !header.isHidden()) {
            hideHeader(getGlobalPositionOf(header as T), header)
        }
    }

    private fun hideHeader(position: Int, header: IHeader<*>?) {
        if (position >= 0) {
            Log.d("Hiding header position=$position header=$header", TAG)
            header?.setHidden(true)
            // Remove and notify removals
            listData.removeAt(position)
            notifyItemRemoved(position)
        }
    }


    /**
     * Internal method to link the header to the new item.
     *
     * Used by the Adapter during the Remove/Restore/Move operations.
     * The new item looses the previous header, and if the old header is not shared,
     * old header is added to the orphan list.
     *
     * @param item    the item that holds the header
     * @param header  the header item
     * @param payload any non-null user object to notify the header and the item (the payload
     * will be therefore passed to the bind method of the items ViewHolder),
     * pass null to not notify the header and item
     */
    private fun linkHeaderTo(item: T?, header: IHeader<*>?, payload: Any?) {
        if (item != null && item is ISectionable<*, *>) {
            // Unlink header only if different
            if (item.getHeader() != null && header != item.getHeader()) {
                unlinkHeaderFrom(item, Payload.UNLINK)
            }
            if (item.getHeader() == null && header != null) {
                Log.d("Link header $header to $item", TAG)
                //TODO: try-catch for when sectionable item has a different header class signature, if so, they just can't accept that header!
                item.setHeader(header)
                // Notify items
                if (payload != null) {
                    if (!header.isHidden()) notifyItemChanged(
                        getGlobalPositionOf(header as T),
                        payload
                    )
                    if (!item.isHidden()) notifyItemChanged(getGlobalPositionOf(item), payload)
                }
            }
        } else {
            if (header != null) {
                notifyItemChanged(getGlobalPositionOf(header as T), payload)
            }
        }
    }

    /**
     * Internal method to unlink the header from the specified item.
     *
     * Used by the Adapter during the Remove/Restore/Move operations.
     *
     * @param item    the item that holds the header
     * @param payload any non-null user object to notify the header and the item (the payload
     * will be therefore passed to the bind method of the items ViewHolder),
     * pass null to notnotify the header and item
     */
    private fun unlinkHeaderFrom(item: T, payload: Any?) {
        if (hasHeader(item)) {
            val sectionable = item as ISectionable<*, *>
            val header = sectionable.getHeader()
            Log.d("Unlink header $header from $sectionable")
            sectionable.setHeader(null)
            // Notify items
            if (payload != null) {
                if (header != null && !header.isHidden()) notifyItemChanged(
                    getGlobalPositionOf(
                        header as T
                    ), payload
                )
                if (!item.isHidden()) notifyItemChanged(getGlobalPositionOf(item), payload)
            }
        }
    }


    /*--------------------------------------------*/
    /* VIEW HOLDER METHODS ARE DELEGATED TO ITEMS */
    /*--------------------------------------------*/

    /**
     *
     * You **CANNOT** override this method: `FlexibleAdapter` delegates the ViewType
     * definition via `IFlexible.getLayoutRes()` so ViewTypes are automatically mapped
     * (AutoMap).
     *
     * @param position position for which ViewType is requested
     * @return layout resource defined in `IFlexible#getLayoutRes()`
     * @see IFlexible.getItemViewType
     */
    override fun getItemViewType(position: Int): Int {
        val item = getItem(position)
        if (item == null) {
            Log.d("Item for ViewType not found! position=$position, items=$itemCount", TAG)
            return 0
        }
        // Map the view type if not done yet
        mapViewTypeFrom(item)
        autoMap = true
        return item.getItemViewType()
    }

    /**
     * You **CANNOT** override this method to create the ViewHolder, `FlexibleAdapter`
     * delegates the creation via `IFlexible.createViewHolder()`.
     *
     * @return a new ViewHolder that holds a View of the given view type
     * @see IFlexible.createViewHolder
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val item = getViewTypeInstance(viewType)
        if (item == null || !autoMap) {
            throw IllegalStateException(
                String.format(
                    "ViewType instance not found for viewType %s. You should implement the AutoMap properly.",
                    viewType
                )
            )
        }
        return item.createViewHolder(parent, this)
    }


    /**
     * You **CANNOT** override this method to bind the items. `FlexibleAdapter` delegates
     * the binding to the corresponding item via `IFlexible.bindViewHolder()`.
     *
     * @see IFlexible.bindViewHolder
     */
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        this.onBindViewHolder(holder, position, Collections.unmodifiableList(ArrayList<Any>()))
    }

    /**
     * Same concept of `#onBindViewHolder()` but with Payload.
     *
     * {@inheritDoc}
     *
     * @see IFlexible.bindViewHolder
     * @see onBindViewHolder
     */
    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
        payloads: List<*>
    ) {
        if (!autoMap) {
            // If everything has been set properly, this should never happen ;-)
            throw IllegalStateException("AutoMap is not active, this method cannot be called. You should implement the AutoMap properly.")
        }
        // Bind view activation with current selection
        super.onBindViewHolder(holder, position, payloads)
        // Bind the item
        getItem(position)?.also { item ->
            holder.itemView.isEnabled = item.isEnabled()
            item.bindViewHolder(this, holder, position, payloads)
            // Avoid to show the double background in case header has transparency
            // The visibility will be restored when header is reset in StickyHeaderHelper
            if (areHeadersSticky() &&
                isHeader(item) &&
                !isFastScroll &&
                (mStickyHeaderHelper?.getStickyPosition() ?: -1 >= 0) &&
                payloads.isEmpty()
            ) {
                val headerPos = getFlexibleLayoutManager().findFirstVisibleItemPosition() - 1
                if (headerPos == position) {
                    holder.itemView.invisible()
                }
            }
        }
        // Endless Scroll
        onLoadMore(position)

        // Scroll Animations
        animateView(holder, position)
    }

    @CallSuper
    override fun onViewAttachedToWindow(holder: RecyclerView.ViewHolder) {
        val position = holder.adapterPosition
        Log.d("onViewAttached Holder=${holder.javaClass.simpleName} position=$position", TAG)
        getItem(position)?.onViewAttached(this, holder, position)
    }

    @CallSuper
    override fun onViewDetachedFromWindow(holder: RecyclerView.ViewHolder) {
        val position = holder.adapterPosition
        Log.d("onViewDetached Holder=${holder.javaClass.simpleName} position=$position", TAG)
        getItem(position)?.onViewDetached(this, holder, position)
    }

    @CallSuper
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (areHeadersSticky()) {
            //  Empty (Invisible) Header Item when Using Sticky Headers
            holder.itemView.visible()
        }
        val position = holder.adapterPosition
        getItem(position)?.unbindViewHolder(this, holder, position)
    }

    /*------------------------*/
    /* ENDLESS SCROLL METHODS */
    /*------------------------*/

    fun isEndlessLoading(): Boolean {
        return endlessLoading
    }

    /**
     * @param position
     * @return if item is same progress item
     */

    fun isEndlessProgressItem(position: Int): Boolean {
        return isEndlessProgressItemOf(getItem(position))
    }

    fun isEndlessProgressItemOf(item: T?): Boolean {
        return item == mProgressItem
    }

    /**
     * @param position
     * @return if item of position is progress item set
     */
    fun isEndlessProgress(position: Int): Boolean {
        return getItem(position) != null && getItem(position) == mProgressItem
    }

    /**
     * @return true if loading more will add items to the top, false to the bottom.
     */
    fun isTopEndless(): Boolean {
        return mTopEndless
    }

    /**
     * Sets endless scrolling from the top. All item will be added to the top of the list.
     * Default value is `false` (bottom endless scrolling).
     *
     * @param topEndless true to enableItem endless scrolling from the top, false from the bottom
     */
    fun setTopEndless(topEndless: Boolean) {
        mTopEndless = topEndless
    }

    /**
     * Evaluates if the Adapter is in Endless Scroll mode. When no more load, this method will
     * return `false`. To enableItem again the progress item you MUST be set again.
     *
     * @return true if the progress item is set, false otherwise
     * @see setEndlessProgressItem
     */
    fun isEndlessScrollEnabled(): Boolean {
        return endlessScrollEnabled
    }

    /**
     * Set Endless Scroll Enable
     * @see setEndlessProgressItem
     */

    fun setEndlessScrollEnable(enable: Boolean) {
        endlessScrollEnabled = enable
    }

    /**
     * Provides the current endless page if the page size limit is set, if not set the returned
     * value is always 0.
     *
     * @return the current endless page
     * @see getEndlessPageSize
     * @see setEndlessPageSize
     */
    fun getEndlessCurrentPage(): Int {
        return if (mEndlessPageSize > 0) {
            ceil(getMainItemCount().toDouble() / mEndlessPageSize).toInt()
        } else 0
    }

    /**
     * The current setting for the endless page size limit.
     *
     * **Tip:** This limit is ignored if value is 0.
     *
     * @return the page size limit, if the limit is not set, 0 is returned.
     * @see getEndlessCurrentPage
     * @see setEndlessPageSize
     */
    fun getEndlessPageSize(): Int {
        return mEndlessPageSize
    }

    /**
     * Sets the limit to automatically disable the endless feature when coming items size is less
     * than the *page size*.
     *
     * When endless feature is disabled a [.notifyItemChanged] with payload
     * [Payload.NO_MORE_LOAD] will be triggered on the progressItem, so you can display a
     * message or change the views in this item.
     * Default value is 0 (limit is ignored).
     *
     * @param endlessPageSize the size limit for each page
     * @return this Adapter, so the call can be chained
     */
    fun setEndlessPageSize(@IntRange(from = 0) endlessPageSize: Int): FlexibleAdapter<T> {
        Log.d("Set endlessPageSize=$endlessPageSize", TAG)
        mEndlessPageSize = endlessPageSize
        return this
    }


    /**
     * The current setting for the endless target item count limit.
     *
     * **Tip:** This limit is ignored if value is 0.
     *
     * @return the target items count limit, if the limit is not set, 0 is returned.
     * @see setEndlessTargetCount
     */
    fun getEndlessTargetCount(): Int {
        return mEndlessTargetCount
    }


    /**
     * Sets the limit to automatically disable the endless feature when the total items count in
     * the Adapter is equals or bigger than the *target count*.
     *
     * When endless feature is disabled a [.notifyItemChanged] with payload
     * [Payload.NO_MORE_LOAD] will be triggered on the progressItem, so you can display a
     * message or change the views in this item.
     * Default value is 0 (limit is ignored).
     *
     * @param endlessTargetCount the total items count limit
     * @return this Adapter, so the call can be chained
     * @see getEndlessTargetCount
     */
    fun setEndlessTargetCount(@IntRange(from = 0) endlessTargetCount: Int): FlexibleAdapter<T> {
        Log.d("Set endlessTargetCount=$endlessTargetCount")
        mEndlessTargetCount = endlessTargetCount
        return this
    }

    /**
     * Sets if Endless / Loading More should be triggered at start-up, especially when the
     * list is empty.
     *
     * Default value is `false`.
     *
     * @param enable true to trigger a loading at start up, false to trigger loading with binding
     * @return this Adapter, so the call can be chained
     */
    fun setLoadingMoreAtStartUp(enable: Boolean): FlexibleAdapter<T> {
        Log.d("Set loadingAtStartup=$enable", TAG)
        if (enable) {
            coroutineScope.launch(Dispatchers.Main) { onLoadMore(0) }
        }
        return this
    }


    /**
     * Sets the progressItem to be displayed at the end of the list and activate the Loading More
     * feature.
     *
     * Using this method, the [EndlessScrollListener] won't be called so that you can
     * handle a click event to load more items upon a user request.
     * To correctly implement "Load more upon a user request" check the Wiki page of this library.
     *
     * @param progressItem the item representing the progress bar
     * @return this Adapter, so the call can be chained
     * @see isEndlessScrollEnabled
     * @see setEndlessScrollListener
     */
    fun setEndlessProgressItem(progressItem: T?): FlexibleAdapter<T> {
        endlessScrollEnabled = progressItem != null

        if (progressItem != null) {
            setEndlessScrollThreshold(mEndlessScrollThreshold)
            mProgressItem = progressItem
            Log.d("Enabled EndlessScrolling Item=$progressItem  enable=$endlessScrollEnabled", TAG)
        } else {
            hideProgressItem()
            mProgressItem = null
        }
        return this
    }


    /**
     * Sets the progressItem to be displayed at the end of the list and Sets the callback to
     * automatically load more items asynchronously(your duty) (no further user action is needed
     * but the scroll).
     *
     * @param endlessScrollListener the callback to invoke the asynchronous loading
     * @param progressItem          the item representing the progress bar
     * @return this Adapter, so the call can be chained
     * @see isEndlessScrollEnabled
     * @see setEndlessProgressItem
     */
    fun setEndlessScrollListener(
        endlessScrollListener: EndlessScrollListener,
        progressItem: T
    ): FlexibleAdapter<T> {
        Log.d("Set endlessScrollListener=$endlessScrollListener", TAG)
        onEndlessScrollListener = endlessScrollListener
        return setEndlessProgressItem(progressItem)
    }


    /**
     * Sets the minimum number of items still to bind to start the automatic loading.
     *
     * Default value is 1.
     *
     * @param thresholdItems minimum number of unbound items to start loading more items
     * @return this Adapter, so the call can be chained
     */
    fun setEndlessScrollThreshold(@IntRange(from = 1) thresholdItems: Int): FlexibleAdapter<T> {
        var thresholdItemsResult = thresholdItems
        if (recyclerViewHasInitialized()) {
            // Increase visible threshold based on number of columns
            if (flexibleLayoutManagerHasInitialized()) {
                thresholdItemsResult *= getFlexibleLayoutManager().getSpanCount()
            }
        }
        mEndlessScrollThreshold = thresholdItemsResult
        Log.d("Set endlessScrollThreshold=$mEndlessScrollThreshold", TAG)
        return this
    }


    /**
     * This method is called automatically when an item is bound.
     *
     * @param position the current binding position
     */
    open fun onLoadMore(position: Int) {
        // Skip everything when loading more is unused OR currently loading
        if (!isEndlessScrollEnabled() || endlessLoading || isEndlessProgressItem(position))
            return

        // Check next loading threshold
        val threshold = if (mTopEndless)
            mEndlessScrollThreshold - (if (hasFilter()) 0 else mScrollableHeaders.size)
        else
            itemCount - mEndlessScrollThreshold - if (hasFilter()) 0 else mScrollableFooters.size

        if (!mTopEndless && (position == getGlobalPositionOf(mProgressItem) || position < threshold) || mTopEndless && position > 0 && position > threshold) {
            return
        }
        Log.d(
            "onLoadMore:   topEndless=$mTopEndless\nloading=$endlessLoading\nposition=$position\nitemCount=$itemCount\nthreshold=$mEndlessScrollThreshold\ncurrentThreshold=$threshold",
            TAG
        )
        // Load more if not loading and inside the threshold
        endlessLoading = true
        // Insertion is using Coroutines, as suggested by Android because: java.lang.IllegalStateException:
        // Cannot call notifyItemInserted while RecyclerView is computing a layout or scrolling
        coroutineScope.launch(Dispatchers.Main) {
            // Show progressItem if not already shown
            showProgressItem()
            Log.d("onLoadMore     invoked!")
            onEndlessScrollListener?.onLoadMore(this@FlexibleAdapter, getMainItemCount(), getEndlessCurrentPage())
        }
    }

    /**
     * Call this method to complete the action of the Loading more items.
     * When [noMoreLoad] OR [onError] OR [onCancel], pass empty list or null to hide the
     * progressItem. When limits are set, endless feature will be disabled. To enableItem
     * @see setEndlessProgressItem again call
     * Optionally you can pass a delay time to still display the item with the latest information
     * inside. The message has to be handled inside the [bindViewHolder] of the item.
     * @see notifyItemChanged(int, Object) with payload  [Payload.NO_MORE_LOAD]
     * will be triggered on the progressItem, so you can display a message or change the views in
     * this item.
     *
     * @param newItems the list of the new items, can be empty or null
     * @param delay    the delay used to remove the progress item or -1 to disable the
     *                 loading forever and to keep the progress item visible.
     */
    open fun onLoadMoreComplete(newItems: List<T>, @IntRange(from = 0) delay: Long = 0L) {
        // Reset the loading status
        endlessLoading = false
        // Calculate new items count
        val newItemsSize = newItems.size
        val totalItemCount = newItemsSize + getMainItemCount()
        val progressPosition = getGlobalPositionOf(mProgressItem)
        // Check if features are enabled and the limits have been reached
        if (mEndlessPageSize > 0 && newItemsSize < mEndlessPageSize || // Is feature enabled and Not enough items?
            mEndlessTargetCount in 1..totalItemCount
        ) { // Is feature enabled and Max limit has been reached?
            // Disable the EndlessScroll feature
            Log.d("onLoadMore     disable endless", TAG)
            setEndlessProgressItem(null)
        }
        // Remove the progressItem if needed.
        // Don't remove progressItem if delay is negative (-1) to keep it visible.
        if (delay > 0 && (newItemsSize == 0)) {
            Log.d("onLoadMore     enqueued removing progressItem ($delay ms)", TAG)
            coroutineScope.launch(Dispatchers.Main) {
                delay(delay)
                hideProgressItem()
            }
        } else if (delay >= 0) {
            hideProgressItem()
        }


        // Add any new items
        if (newItemsSize > 0) {
            Log.d(
                "onLoadMore     performing adding $newItemsSize new items on page=${getEndlessCurrentPage()}",
                TAG
            )
            addItems(progressPosition, newItems)
        }
        // Eventually notify noMoreLoad
        if (newItemsSize == 0 || !isEndlessScrollEnabled()) {
            noMoreLoad(newItemsSize)
        }
    }

    /**
     * Called at each loading more.
     */
    private fun showProgressItem() {
        Log.d("onLoadMore     show progressItem", TAG)

        mProgressItem?.also {
            if (mTopEndless) {
                addScrollableHeader(it)
            } else {
                addScrollableFooter(it)
            }
        }
    }

    /**
     * Called when loading more should continue.
     */
    private fun hideProgressItem() {
        val positionToNotify = getGlobalPositionOf(mProgressItem)
        if (positionToNotify >= 0) {
            Log.d("onLoadMore     remove progressItem", TAG)
            mProgressItem?.also {
                if (mTopEndless) {
                    removeScrollableHeader(it)
                } else {
                    removeScrollableFooter(it)
                }
            }
        }
    }


    /**
     * Called when no more items are loaded.
     */
    private fun noMoreLoad(newItemsSize: Int) {
        Log.d("noMoreLoad!", TAG)
        val positionToNotify = getGlobalPositionOf(mProgressItem)
        if (positionToNotify >= 0) notifyItemChanged(positionToNotify, Payload.NO_MORE_LOAD)
        onEndlessScrollListener?.noMoreLoad(this, newItemsSize)
    }

    /*--------------------*/
    /* EXPANDABLE METHODS */
    /*--------------------*/

    /**
     * @return true if `collapseOnExpand` is enabled, false otherwise
     */
    fun isAutoCollapseOnExpand(): Boolean {
        return collapseOnExpand
    }

    /**
     * Automatically collapse all previous expanded parents before expand the new clicked parent.
     *
     * Default value is `false` (disabled).
     * **Tip:** This parameter works in collaboration with [.setMinCollapsibleLevel].
     *
     * @param collapseOnExpand true to collapse others items, false to just expand the current
     * @return this Adapter, so the call can be chained
     * @see setMinCollapsibleLevel
     */
    fun setAutoCollapseOnExpand(collapseOnExpand: Boolean): FlexibleAdapter<T> {
        Log.d("Set autoCollapseOnExpand=$collapseOnExpand", TAG)
        this.collapseOnExpand = collapseOnExpand
        return this
    }

    /**
     * @return true if `collapseSubLevels` is enabled, false otherwise
     */
    fun isRecursiveCollapse(): Boolean {
        return collapseSubLevels
    }


    /**
     * Automatically collapse all inner sub expandable when higher parent is collapsed.<br></br>
     * By keeping this parameter `false`, their expanded status remains `expanded=true`
     * so when the higher parent is expanded again, the sub expandables will appear again expanded.
     *
     * Default value is `false` (keep expanded status).
     * **Tip:** This parameter works in collaboration with [.setMinCollapsibleLevel].
     *
     * @param collapseSubLevels true to allow inner sub expandable to collapse, false to keep the expanded status
     * @return this Adapter, so the call can be chained
     * @see setMinCollapsibleLevel
     */
    fun setRecursiveCollapse(collapseSubLevels: Boolean): FlexibleAdapter<T> {
        Log.d("Set setAutoCollapseSubLevels=$collapseSubLevels", TAG)
        this.collapseSubLevels = collapseSubLevels
        return this
    }

    /**
     * @return true if `scrollOnExpand` is enabled, false otherwise
     */
    fun isAutoScrollOnExpand(): Boolean {
        return scrollOnExpand
    }

    /**
     * Automatically scroll the clicked expandable item to the first visible position.<br></br>
     *
     * Default value is `false` (disabled).
     * **Note:** This works ONLY in combination with `SmoothScrollLinearLayoutManager`
     * or with `SmoothScrollGridLayoutManager` available in UI extension.
     *
     * @param scrollOnExpand true to enableItem automatic scroll, false to disable
     * @return this Adapter, so the call can be chained
     */
    fun setAutoScrollOnExpand(scrollOnExpand: Boolean): FlexibleAdapter<T> {
        Log.d("Set setAutoScrollOnExpand=$scrollOnExpand", TAG)
        this.scrollOnExpand = scrollOnExpand
        return this
    }

    /**
     * @param position the position of the item to check
     * @return true if the item implements [IExpandable] interface and its property has
     * `expanded = true`
     */
    fun isExpanded(@IntRange(from = 0) position: Int): Boolean {
        return isExpanded(getItem(position))
    }

    /**
     * Checks if the provided item is an [IExpandable] instance and is expanded.
     *
     * @param item the item to check
     * @return true if the item implements [IExpandable] interface and its property has
     * `expanded = true`
     */
    fun isExpanded(item: T?): Boolean {
        return (item as? IExpandable<*, *>)?.isExpanded() ?: false
    }


    /**
     * Checks if the provided item is an [IExpandable] instance.
     *
     * @param item the item to check
     * @return true if the item implements [IExpandable] interface, false otherwise
     */
    fun isExpandableItem(item: T?): Boolean {
        return item is IExpandable<*, *>
    }

    /**
     * @return the level of the minimum collapsible level used in MultiLevel expandable
     */
    fun getMinCollapsibleLevel(): Int {
        return mMinCollapsibleLevel
    }

    /**
     * Sets the minimum level which all sub expandable items will be collapsed too.
     *
     * Default value is [.mMinCollapsibleLevel] (All levels including 0).
     * **Tip:** This parameter works in collaboration with [.setRecursiveCollapse].
     *
     * @param minCollapsibleLevel the minimum level to auto-collapse sub expandable items
     * @return this Adapter, so the call can be chained
     * @see setRecursiveCollapse
     */
    fun setMinCollapsibleLevel(minCollapsibleLevel: Int): FlexibleAdapter<T> {
        Log.d("Set minCollapsibleLevel=$minCollapsibleLevel", TAG)
        this.mMinCollapsibleLevel = minCollapsibleLevel
        return this
    }

    /**
     * Utility method to check if the expandable item has sub items.
     *
     * @param expandable the [IExpandable] object
     * @return true if the expandable has subItems, false otherwise
     */
    fun hasSubItems(expandable: IExpandable<*, *>?): Boolean {
        return expandable != null && expandable.getSubItems().isNotEmpty()
    }

    /**
     * Retrieves the parent of a child for the provided position.
     *
     * Only for a real child of an expanded parent.
     *
     * @param position the position of the child item
     * @return the parent of this child item or null if item has no parent
     */
    fun getExpandableOf(position: Int): IExpandable<*, *>? {
        return getExpandableOf(getItem(position))
    }

    /**
     * Retrieves the parent of a child.
     *
     * Only for a real child of an expanded parent.
     *
     * @param child the child item
     * @return the parent of this child item or null if item has no parent
     * @see getExpandablePositionOf
     * @see getSubPositionOf
     */
    fun getExpandableOf(child: T?): IExpandable<*, *>? {
        for (parent in listData) {
            if (isExpandableItem(parent)) {
                val expandable = parent as IExpandable<*, *>
                if (expandable.isExpanded() && hasSubItems(expandable)) {
                    for (subItem in expandable.getSubItems()) {
                        //Pick up only no-hidden items
                        if (!subItem.isHidden() && subItem == child)
                            return expandable
                    }
                }
            }
        }
        return null
    }

    /**
     * Retrieves the parent position of a child.
     *
     * Only for a real child of an expanded parent.
     *
     * @param child the child item
     * @return the parent position of this child item or -1 if not found
     * @see getExpandableOf
     * @see getSubPositionOf
     */
    fun getExpandablePositionOf(child: T): Int {
        return getGlobalPositionOf(getExpandableOf(child) as? T)
    }

    /**
     * Retrieves the sub position of any sub item in the section where it lays. First position
     * corresponds to `0`.
     *
     * Works for items under header and under expandable too.
     *
     * @param child any sub item of any section
     * @return the position in the parent or -1 if the child is a parent/header itself or not found
     * @see getSameTypePositionOf
     * @see getExpandableOf
     * @see getExpandablePositionOf
     */
    fun getSubPositionOf(child: T): Int {
        // If a sectionable has header, we take the global position of both
        // and calculate the difference. Expandable will have precedence.
        if (child is ISectionable<*, *> && hasHeader(child)) {
            val header = getHeaderOf(child)
            if (header !is IExpandable<*, *>) {
                return getGlobalPositionOf(child) - getGlobalPositionOf(header as? T) - 1
            }
        }
        return getSiblingsOf(child).indexOf(child)
    }

    /**
     * Provides the full sub list where the child currently lays.
     *
     * @param child the child item
     * @return the list of the child element, or an empty list if the child item has no parent
     * @see getExpandableOf
     * @see getExpandablePositionOf
     * @see getSubPositionOf
     * @see getExpandedItems
     */
    fun getSiblingsOf(child: T): List<T> {
        val expandable = getExpandableOf(child)
        return expandable?.getSubItems() as? List<T> ?: listOf()
    }

    /**
     * Provides a list of all expandable items that are currently expanded.
     *
     * @return a list with all expanded items
     * @see getSiblingsOf
     * @see getExpandedPositions
     */
    fun getExpandedItems(): List<T> {
        return listData.filter { isExpandableItem(it) }
    }


    /**
     * Provides a list of all expandable positions that are currently expanded.
     *
     * @return a list with the global positions of all expanded items
     * @see getSiblingsOf
     * @see getExpandedItems
     */
    fun getExpandedPositions(): List<Int> {
        val expandedPositions = mutableListOf<Int>()
        val startPosition = max(0, mScrollableHeaders.size - 1)
        val endPosition = itemCount - mScrollableFooters.size - 1
        for (i in startPosition until endPosition) {
            if (isExpanded(getItem(i))) expandedPositions.add(i)
        }
        return expandedPositions
    }

    /**
     * Recursively determine the total number of items between a range of expandable subItems.
     *
     * @return item count, including recursive expansions, to the first level sub-position item
     */
    private fun getRecursiveSubItemCount(parent: IExpandable<*, *>, subPosition: Int): Int {
        var count = 0
        // Get the subItems
        val subItems = parent.getSubItems()
        // Iterate through subItems
        for (index in 0 until subPosition) {
            // Check whether item is also expandable and expanded
            val item = if (subItems hasPosition index) subItems[index] as T else null
            if (isExpanded(item)) {
                val subExpandable = item as IExpandable<*, *>
                count += getRecursiveSubItemCount(subExpandable, subExpandable.getSubItems().size)
            }
            count++
        }
        return count
    }

    /**
     * Convenience method to expand a single item. Parent will be notified.
     *
     * Expands an item that is Expandable, not yet expanded, that has subItems and
     * no child is selected.
     * If configured, automatic smooth scroll will be performed.
     *
     * @param item the item to expand, must be an Expandable and present in the list
     * @return the number of subItems expanded
     * @see expand
     * @see expandAll
     */
    fun expand(
        item: T,
        expandAll: Boolean = false,
        init: Boolean = false,
        notifyParent: Boolean = false
    ): Int {
        return expand(getGlobalPositionOf(item), expandAll, init, notifyParent)
    }


    /**
     * Convenience method to initially expand a single item.
     *
     * **Note:** Must be used in combination with adding new items that require to be
     * initially expanded.
     * **WARNING!**
     * <br></br>Expanded status is ignored if `init = true`: it will always attempt to expand
     * the item: If subItems are already visible <u>and</u> the new item has status expanded, the
     * subItems will appear duplicated(!) and the automatic smooth scroll will be skipped!
     *
     * @param position     the position of the item to expand
     * @param init         true to initially expand item
     * @param notifyParent true to notify the parent with [Payload.EXPANDED]
     * @return the number of subItems expanded
     */
    fun expand(
        position: Int,
        expandAll: Boolean = false,
        init: Boolean = false,
        notifyParent: Boolean = false
    ): Int {
        var newPosition = position
        val item = getItem(newPosition)

        if (item == null || !isExpandableItem(item)) return 0

        val expandable = item as IExpandable<*, *>
        if (!hasSubItems(expandable)) {
            expandable.setExpanded(false) // Clear the expanded flag
            Log.d(
                "No subItems to Expand on position $newPosition expanded ${expandable.isExpanded()}",
                TAG
            )
            return 0
        }
        if (!init && !expandAll) {
            Log.d(
                "Request to Expand on position=$newPosition expanded=${expandable.isExpanded()} anyParentSelected=$parentSelected",
                TAG
            )
        }
        var subItemsCount = 0
        if (init || !expandable.isExpanded() && (!parentSelected || expandable.getExpansionLevel() <= mSelectedLevel)) {

            // Collapse others expandable if configured so Skip when expanding all is requested
            // Fetch again the new position after collapsing all!!
            if (collapseOnExpand && !expandAll && collapseAll(mMinCollapsibleLevel) > 0) {
                newPosition = getGlobalPositionOf(item)
            }

            // Every time an expansion is requested, subItems must be taken from the
            // original Object and without the subItems marked hidden (removed)
            val subItems = getExpandableList(expandable, true)
            listData.addAll(newPosition + 1, subItems)
            subItemsCount = subItems.size
            // Save expanded state
            expandable.setExpanded(true)

            // Automatically smooth scroll the current expandable item to show as much
            // children as possible
            if (!init && scrollOnExpand && !expandAll) {
                autoScrollWithDelay(newPosition, subItemsCount)
            }

            // Expand!
            if (notifyParent) notifyItemChanged(newPosition, Payload.EXPANDED)
            if (!init) {
                notifyItemRangeInserted(newPosition + 1, subItemsCount) //notify if not init
            }
            // Show also the headers of the subItems
            if (!init && headersShown) {
                var count = 0
                for (subItem in subItems) {
                    if (showHeaderOf(newPosition + (++count), subItem, false)) count++
                }
            }

            // Expandable as a Scrollable Header/Footer
            if (!expandSHF(mScrollableHeaders, expandable))
                expandSHF(mScrollableFooters, expandable)

            Log.d(
                "$subItemsCount $newPosition subItems on position=${if (init) "Initially expanded" else "Expanded"}",
                TAG
            )
        }
        return subItemsCount
    }

    private fun expandSHF(scrollables: MutableList<T>, expandable: IExpandable<*, *>): Boolean {
        val index = scrollables.indexOf(expandable as T)
        return if (index >= 0) {
            if (index + 1 < scrollables.size) {
                scrollables.addAll(index + 1, expandable.getSubItems() as List<T>)
            } else {
                scrollables.addAll(expandable.getSubItems() as List<T>)
            }
        } else false
    }

    /**
     * Expands all `IExpandable` items with at least the specified level.
     *
     * Parents will be notified.
     *
     * @param level the minimum level to expand the sub expandable items
     * @return the number of parent successfully expanded
     * @see expandAll
     * @see setMinCollapsibleLevel
     */
    fun expandAll(level: Int = mMinCollapsibleLevel): Int {
        var expanded = 0
        // More efficient if we expand from First expandable position
        val startPosition = max(0, mScrollableHeaders.size - 1)
        var i = startPosition
        while (i < itemCount - mScrollableFooters.size) {
            val item = getItem(i)
            if (isExpandableItem(item)) {
                val expandable = item as IExpandable<*, *>
                if (expandable.getExpansionLevel() <= level && expand(i, true, false, true) > 0) {
                    i += expandable.getSubItems().size
                    expanded++
                }
            }
            i++
        }
        return expanded
    }


    /**
     *
     * @param position     the position of the item to collapse
     * @param notifyParent notify the parent with [Payload.COLLAPSED]
     * @return the number of subItems collapsed
     * @see collapseAll
     */
    fun collapse(@IntRange(from = 0) position: Int, notifyParent: Boolean = false): Int {
        val item = getItem(position)
        if (item == null || !isExpandableItem(item)) return 0

        val expandable = item as IExpandable<*, *>
        // Take the current subList (will improve the performance when collapseAll)
        val subItems = getExpandableList(expandable, true)
        var subItemsCount = subItems.size

        Log.d(
            "Request to Collapse on position=$position expanded=${expandable.isExpanded()} hasSubItemsSelected=${
                hasSubItemsSelected(
                    position,
                    subItems
                )
            }", TAG
        )

        if (expandable.isExpanded() && subItemsCount > 0 && (!hasSubItemsSelected(
                position,
                subItems
            ) || getPendingRemovedItem(item) != null)
        ) {

            // Recursive collapse of all sub expandable
            if (collapseSubLevels) {
                recursiveCollapse(position + 1, subItems, expandable.getExpansionLevel())
            }
            listData.removeAll(subItems)
            subItemsCount = subItems.size
            // Save expanded state
            expandable.setExpanded(false)

            // Collapse!
            if (notifyParent) notifyItemChanged(position, Payload.COLLAPSED)
            notifyItemRangeRemoved(position + 1, subItemsCount)

            // Hide also the headers of the subItems
            if (headersShown && !isHeader(item)) {
                for (subItem in subItems) {
                    hideHeaderOf(subItem)
                }
            }

            // Expandable as a Scrollable Header/Footer
            if (!collapseSHF(mScrollableHeaders, expandable))
                collapseSHF(mScrollableFooters, expandable)

            Log.d("Collapsed $subItemsCount subItems on position $position", TAG)
        }
        return subItemsCount
    }

    private fun collapseSHF(scrollables: MutableList<T>, expandable: IExpandable<*, *>): Boolean {
        return scrollables.contains(expandable as T) && scrollables.removeAll(expandable.getSubItems())
    }

    private fun recursiveCollapse(startPosition: Int, subItems: List<T>, level: Int): Int {
        var collapsed = 0
        for (i in subItems.indices.reversed()) {
            val subItem = subItems[i]
            if (isExpanded(subItem)) {
                val expandable = subItem as IExpandable<*, *>
                if (expandable.getExpansionLevel() >= level && collapse(
                        startPosition + i,
                        true
                    ) > 0
                ) {
                    collapsed++
                }
            }
        }
        return collapsed
    }


    /**
     * Collapses all expandable items with the level equals-higher than the specified level.
     *
     * Parents will be notified.
     *
     * @param level the level to start collapse sub expandable items
     * @return the number of parent successfully collapsed
     * @see collapse
     */
    fun collapseAll(level: Int = mMinCollapsibleLevel): Int {
        return recursiveCollapse(0, listData, level)
    }

    /*----------------*/
    /* UPDATE METHODS */
    /*----------------*/

    /**
     * Updates/Rebounds the itemView corresponding to the current position of the
     * provided item with the new content.
     *
     * @param item    the item with the new content
     * @param payload any non-null user object to notify the current item (the payload will be
     * therefore passed to the bind method of the item ViewHolder to optimize the
     * content to update); pass null to rebind all fields of this item.
     */
    fun updateItem(item: T, payload: Any? = null) {
        updateItem(getGlobalPositionOf(item), item, payload)
    }

    /**
     * Updates/Rebounds the itemView corresponding to the current position of the
     * provided item with the new content. Use [.updateItem] if the
     * new content should be bound on the same position.
     *
     * @param position the position where the new content should be updated and rebound
     * @param item     the item with the new content
     * @param payload  any non-null user object to notify the current item (the payload will be
     * therefore passed to the bind method of the item ViewHolder to optimize the
     * content to update); pass null to rebind all fields of this item.
     */
    fun updateItem(@IntRange(from = 0) position: Int, item: T, payload: Any? = null) {
        val itemCount = itemCount
        if (position < 0 || position >= itemCount) {
            Log.d("Cannot updateItem on position out of OutOfBounds!")
            return
        }
        listData[position] = item
        Log.d("updateItem notifyItemChanged on position $position")
        notifyItemChanged(position, payload)
    }


    /*----------------*/
    /* ADDING METHODS */
    /*----------------*/

    /**
     * Inserts the given item at desired position or Add item at last position with a delay
     * and auto-scroll to the position.
     *
     * Scrolling animation is automatically preserved, meaning that, notification for animation
     * is ignored.
     * Useful at startup, when there's an item to add after Adapter Animations is completed.
     *
     * @param position         position of the item to add
     * @param item             the item to add
     * @param delay            a non-negative delay
     * @param scrollToPosition true if RecyclerView should scroll after item has been added,
     * false otherwise
     * @see addItem
     * @see addItems
     * @see addSubItems
     * @see removeItemWithDelay
     */
    fun addItemWithDelay(
        @IntRange(from = 0) position: Int,
        item: T,
        @IntRange(from = 0) delay: Long,
        scrollToPosition: Boolean
    ) {
        coroutineScope.launch(Dispatchers.Main) {
            delay(delay)
            if (addItem(position, item) && scrollToPosition) autoScrollWithDelay(
                position,
                -1
            )
        }
    }

    /**
     * Simply append the provided item to the end of the list.
     *
     * Convenience method of [.addItem] with
     * `position = getMainItemCount()`.
     *
     * @param item the item to add
     * @return true if the internal list was successfully modified, false otherwise
     */
    fun addItem(item: T): Boolean {
        return addItem(itemCount, item)
    }

    /**
     * Inserts the given item at the specified position or Adds the item to the end of the list
     * (no matters if the new position is out of bounds!).
     *
     * @param position position inside the list, if negative, items will be added to the end
     * @param item     the item to add
     * @return true if the internal list was successfully modified, false otherwise
     * @see addItem
     * @see addItems
     * @see addSubItems
     * @see addItemWithDelay
     */
    fun addItem(@IntRange(from = 0) position: Int, item: T): Boolean {
        Log.d("addItem delegates addition to addItems!", TAG)
        return addItems(position, listOf(item))
    }

    /**
     * Inserts a set of items at specified position or Adds the items to the end of the list
     * (no matters if the new position is out of bounds!).
     *
     * **Note:**
     * <br></br>- When all headers are shown, if exists, the header of this item will be shown as well,
     * unless it's already shown, so it won't be shown twice.
     * <br></br>- Items will be always added between any scrollable header and footers.
     *
     * @param position position inside the list, if negative, items will be added to the end
     * @param items    the set of items to add
     * @return true if the internal list was successfully modified, false otherwise
     * @see addItem
     * @see addItem
     * @see addSubItems
     */
    fun addItems(@IntRange(from = 0) position: Int, items: List<T>): Boolean {
        var newPosition = position
        if (items.isEmpty()) {
            Log.d("addItems No items to add!", TAG)
            return false
        }
        val initialCount = getMainItemCount() // Count only main items!
        if (newPosition < 0) {
            Log.d("addItems Position is negative! adding items to the end", TAG)
            newPosition = initialCount + mScrollableHeaders.size
        }
        // Insert the items properly
        performInsert(newPosition, items, true)
        // Show the headers of new items
        showOrUpdateHeaders(items)
        // Call listener to update EmptyView
        if (!recursive && !multiRange && initialCount == 0 && itemCount > 0) {
            onUpdateListener?.onUpdateEmptyView(this, getMainItemCount())
        }
        return true
    }

    private fun performInsert(position: Int, items: List<T>, notify: Boolean) {
        var newPosition = position
        val itemCount = itemCount
        if (newPosition < itemCount) {
            listData.addAll(newPosition, items)
        } else {
            listData.addAll(items)
            newPosition = itemCount
        }
        // Notify range addition
        if (notify) {
            Log.d("addItems on position=$newPosition itemCount=${items.size}", TAG)
            notifyItemRangeInserted(newPosition, items.size)
        }
    }

    /*
     * Newly inserted headers won't be updated, but will be updated only once
     * if the new inserted items initially had their header visible.
     */
    private fun showOrUpdateHeaders(items: List<T>) {
        if (headersShown && !recursive) {
            recursive = true
            // Use Set to uniquely save objects
            val headersInserted = hashSetOf<T>()
            val headersToUpdate = hashSetOf<T>()
            for (item in items) {
                val header = getHeaderOf(item) ?: continue
                // We have to find the correct position due to positions changes!
                if (showHeaderOf(getGlobalPositionOf(item), item, false)) {
                    // We will skip all the newly inserted headers from being bound again
                    headersInserted.add(header as T)
                } else {
                    // Save header for unique update
                    headersToUpdate.add(header as T)
                }
            }
            // Notify headers uniquely
            headersToUpdate.removeAll(headersInserted)
            for (header in headersToUpdate) {
                notifyItemChanged(getGlobalPositionOf(header), Payload.CHANGE)
            }
            recursive = false
        }
    }

    /**
     * Convenience method of [.addSubItem].
     * <br></br>In this case parent item will never be expanded if it is collapsed.
     *
     * @return true if the internal list was successfully modified, false otherwise
     * @see addSubItems
     */
    fun addSubItem(
        @IntRange(from = 0) parentPosition: Int,
        @IntRange(from = 0) subPosition: Int, item: T
    ): Boolean {
        return this.addSubItem(parentPosition, subPosition, item, false, Payload.CHANGE)
    }

    /**
     * Convenience method of [.addSubItems].
     * <br></br>Optionally you can pass any payload to notify the parent about the change and optimize
     * the view binding.
     *
     * @param parentPosition position of the expandable item that shall contain the subItem
     * @param subPosition    the position of the subItem in the expandable list
     * @param item           the subItem to add in the expandable list
     * @param expandParent   true to initially expand the parent (if needed) and after to add
     * the subItem, false to simply add the subItem to the parent
     * @param payload        any non-null user object to notify the parent (the payload will be
     * therefore passed to the bind method of the parent ViewHolder),
     * pass null to <u>not</u> notify the parent
     * @return true if the internal list was successfully modified, false otherwise
     * @see addSubItems
     */
    fun addSubItem(
        @IntRange(from = 0) parentPosition: Int,
        @IntRange(from = 0) subPosition: Int,
        item: T,
        expandParent: Boolean,
        payload: Any? = null
    ): Boolean {
        // Build a new list with 1 item to chain the methods of addSubItems
        return addSubItems(parentPosition, subPosition, listOf(item), expandParent, payload)
    }

    /**
     * Convenience method of [.addSubItems].
     * <br></br>In this case parent item will never be expanded if it is collapsed.
     *
     * @return true if the internal list was successfully modified, false otherwise
     * @see addSubItems
     */
    fun addSubItems(
        @IntRange(from = 0) parentPosition: Int,
        @IntRange(from = 0) subPosition: Int,
        items: List<T>
    ): Boolean {
        return this.addSubItems(parentPosition, subPosition, items, false, Payload.CHANGE)
    }

    /**
     * Convenience method of [.addSubItems].
     * <br></br>Optionally you can pass any payload to notify the parent about the change and optimize
     * the view binding.
     *
     * @param parentPosition position of the expandable item that shall contain the subItems
     * @param subPosition    the start position in the parent where the new items shall be inserted
     * @param items          the list of the subItems to add
     * @param expandParent   true to initially expand the parent (if needed) and after to add
     * the subItems, false to simply add the subItems to the parent
     * @param payload        any non-null user object to notify the parent (the payload will be
     * therefore passed to the bind method of the parent ViewHolder),
     * pass null to <u>not</u> notify the parent
     * @return true if the internal list was successfully modified, false otherwise
     * @see addSubItems
     */
    fun addSubItems(
        @IntRange(from = 0) parentPosition: Int,
        @IntRange(from = 0) subPosition: Int,
        items: List<T>,
        expandParent: Boolean,
        payload: Any? = null
    ): Boolean {
        val parent = getItem(parentPosition)
        if (isExpandableItem(parent)) {
            val expandable = parent as IExpandable<*, *>?
            return addSubItems(
                parentPosition,
                subPosition,
                expandable!!,
                items,
                expandParent,
                payload
            )
        }
        Log.d("addSubItems Provided parentPosition doesn't belong to an Expandable item!", TAG)
        return false
    }

    /**
     * Adds new subItems on the specified parent item, to the internal list.
     *
     * **In order to add subItems**, the following condition must be satisfied:
     * <br></br>- The item resulting from the parent position is actually an [IExpandable].
     * Optionally, the parent can be expanded and subItems displayed.
     * <br></br>Optionally, you can pass any payload to notify the parent about the change and
     * optimize the view binding.
     *
     * @param parentPosition position of the expandable item that shall contain the subItems
     * @param subPosition    the start position in the parent where the new items shall be inserted
     * @param parent         the expandable item which shall contain the new subItem
     * @param subItems       the list of the subItems to add
     * @param expandParent   true to initially expand the parent (if needed) and after to add
     * the subItems, false to simply add the subItems to the parent
     * @param payload        any non-null user object to notify the parent (the payload will be
     * therefore passed to the bind method of the parent ViewHolder),
     * pass null to <u>not</u> notify the parent
     * @return true if the internal list was successfully modified, false otherwise
     * @see addItems
     */
    private fun addSubItems(
        @IntRange(from = 0) parentPosition: Int,
        @IntRange(from = 0) subPosition: Int,
        parent: IExpandable<*, *>,
        subItems: List<T>,
        expandParent: Boolean,
        payload: Any?
    ): Boolean {
        var added = false
        // Expand parent if requested and not already expanded
        if (expandParent && !parent.isExpanded()) {
            expand(parentPosition)
        }
        // Notify the adapter of the new addition to display it and animate it.
        // If parent is collapsed there's no need to add sub items.
        if (parent.isExpanded()) {
            added = addItems(
                parentPosition + 1 + getRecursiveSubItemCount(parent, subPosition),
                subItems
            )
        }
        // Notify the parent about the change if requested and not already done as Header
        if (payload != null && !isHeader(parent as T)) {
            notifyItemChanged(parentPosition, payload)
        }
        return added
    }


    /**
     * Adds and shows an empty section to the top (position = 0).
     *
     * @return the calculated position for the new item
     * @see addSection
     */
    fun addSection(header: IHeader<*>): Int {
        return addSection(header, null)
    }

    /**
     * Adds and shows an empty section. The new section is a [IHeader] item and the
     * position is calculated after sorting the data set.
     *
     * - To add Sections to the **top**, set null the Comparator object or simply call
     * [.addSection];
     * <br></br>- To add Sections to the **bottom** or in the **middle**, implement a Comparator
     * object able to support <u>all</u> the item types this Adapter is displaying or a
     * ClassCastException will be raised.
     *
     * @param header     the section header item to add
     * @param comparator the criteria to sort the Data Set used to extract the correct position
     * of the new header
     * @return the calculated position for the new item
     */
    fun addSection(header: IHeader<*>, comparator: Comparator<T>?): Int {
        comparator?.also {
            val position = calculatePositionFor(header as T, comparator)
            addItem(position, header as T)
        }
        return -1
    }

    /**
     * Adds a new item in a section when the relative position is **unknown**.
     *
     * The header can be a `IExpandable` type or `IHeader` type.
     * The Comparator object must support <u>all</u> the item types this Adapter is displaying or
     * a ClassCastException will be raised.
     *
     * @param sectionable the item to add
     * @param header      the section receiving the new item
     * @param comparator  the criteria to sort the sectionItems used to extract the correct position
     * of the new item in the section
     * @return the calculated final position for the new item
     * @see addItemToSection
     */
    fun addItemToSection(
        sectionable: ISectionable<*, *>,
        header: IHeader<*>,
        comparator: Comparator<T>
    ): Int {
        val index = if (!header.isHidden()) {
            val sectionItems = mutableListOf<ISectionable<*, *>>()
            sectionItems.addAll(getSectionItems(header))
            sectionItems.add(sectionable)
            //Sort the list for new position
            Collections.sort(sectionItems, comparator as Comparator<in ISectionable<*, *>>)
            //Get the new position
            sectionItems.indexOf(sectionable)
        } else {
            calculatePositionFor(sectionable as T, comparator)
        }
        return addItemToSection(sectionable, header, index)
    }

    /**
     * Adds a new item in a section when the relative position is **known**.
     *
     * The header can be a `IExpandable` type or `IHeader` type.
     *
     * @param sectionable the item to add
     * @param header      the section receiving the new item
     * @param index       the known relative position where to add the new item into the section
     * @return the calculated final position for the new item
     * @see addItemToSection
     */
    fun addItemToSection(
        sectionable: ISectionable<*, *>,
        header: IHeader<*>?,
        @IntRange(from = 0) index: Int
    ): Int {
        Log.d("addItemToSection relativePosition=$index")
        val headerPosition = getGlobalPositionOf(header as T)
        if (index >= 0) {
            sectionable.setHeader(header)
            if (headerPosition >= 0 && isExpandableItem(header as T))
                addSubItem(headerPosition, index, sectionable as T, false, Payload.ADD_SUB_ITEM)
            else
                addItem(headerPosition + 1 + index, sectionable as T)
        }
        return getGlobalPositionOf(sectionable as T)
    }


    /*----------------------*/
    /* DELETE ITEMS METHODS */
    /*----------------------*/

    /**
     * This method clears **everything**: main items, Scrollable Headers and Footers and
     * everything else is displayed.
     *
     * @see clearAllBut
     * @see removeRange
     * @see removeItemsOfType
     */
    fun clear() {
        Log.d("clearAll views")
        removeAllScrollableHeaders()
        removeAllScrollableFooters()
        removeRange(0, itemCount, null)
    }

    /**
     * Clears the Adapter list retaining Scrollable Headers and Footers and all items of the
     * type provided as parameter.
     *
     * **Tip:**- This method is opposite of [.removeItemsOfType].
     *
     * @param viewTypes the viewTypes to retain
     * @see clear
     * @see removeItems
     * @see removeItemsOfType
     */
    fun clearAllBut(vararg viewTypes: Int) {
        Log.d("clearAll retaining views $viewTypes")
        val positionsToRemove = ArrayList<Int>()
        val startPosition = Math.max(0, mScrollableHeaders.size)
        val endPosition = itemCount - mScrollableFooters.size
        for (i in startPosition until endPosition) {
            if (!viewTypes.contains(getItemViewType(i)))
                positionsToRemove.add(i)
        }
        // Remove items by ranges
        removeItems(positionsToRemove)
    }

    /**
     * Removes the given item after the given delay.
     *
     * Scrolling animation is automatically preserved, meaning that notification for animation
     * is ignored.
     *
     * @param item      the item to add
     * @param delay     a non-negative delay
     * @param permanent true to permanently delete the item (no undo), false otherwise
     * @see removeItem
     * @see removeItems
     * @see removeItemsOfType
     * @see removeRange
     * @see removeAllSelectedItems
     * @see addItemWithDelay
     */
    fun removeItemWithDelay(
        item: T, @IntRange(from = 0) delay: Long,
        permanent: Boolean
    ) {
        coroutineScope.launch(Dispatchers.Main) {
            delay(delay)
            performRemove(item, permanent)
        }
    }

    private fun performRemove(item: T, permanent: Boolean) {
        val tempPermanent = permanentDelete
        if (permanent) permanentDelete = true
        removeItem(getGlobalPositionOf(item))
        permanentDelete = tempPermanent
    }


    /**
     * Removes all items of a section, header included.
     *
     * For header that is also expandable, it's equivalent to remove a single item.
     *
     * @param header the head of the section
     * @see removeItem
     */
    fun removeSection(header: IHeader<*>) {
        val sectionItems = getSectionItemPositions(header).toMutableList()
        val headerPos = getGlobalPositionOf(header as T)
        Log.d("removeSection $header with all subItems at position=$headerPos", TAG)
        sectionItems.add(headerPos)
        this.removeItems(sectionItems)
    }

    /**
     * Convenience method of [.removeItem] providing [Payload.CHANGE]
     * as payload for the parent item.
     *
     * @param position the position of item to remove
     * @see removeItems
     * @see removeItemsOfType
     * @see removeRange
     * @see removeAllSelectedItems
     * @see removeItemWithDelay
     * @see removeItem
     */
    fun removeItem(@IntRange(from = 0) position: Int) {
        this.removeItem(position, Payload.CHANGE)
    }


    /**
     * Removes an item from the internal list and notify the change.
     *
     * The item is retained for an eventual Undo.
     * This method delegates the removal to removeRange.
     *
     * @param position The position of item to remove
     * @param payload  any non-null user object to notify the parent (the payload will be
     * therefore passed to the bind method of the parent ViewHolder),
     * pass null to <u>not</u> notify the parent
     * @see removeItems
     * @see removeRange
     * @see removeAllSelectedItems
     * @see removeItem
     */
    fun removeItem(@IntRange(from = 0) position: Int, payload: Any) {
        // Request to collapse after the notification of remove range
        collapse(position)
        Log.d("removeItem delegates removal to removeRange", TAG)
        removeRange(position, 1, payload)
    }

    /**
     * Convenience method of [.removeItems] providing the default
     * [Payload.CHANGE] for the parent items.
     *
     * @see removeItem
     * @see removeItemsOfType
     * @see removeRange
     * @see removeAllSelectedItems
     * @see removeItems
     */
    fun removeItems(selectedPositions: List<Int>) {
        this.removeItems(selectedPositions, Payload.REM_SUB_ITEM)
    }

    /**
     * Removes items by **ranges**, auto-collapse expanded items and notify the change.
     *
     * Every item is retained for an eventual Undo.
     * Optionally you can pass any payload to notify the parent items about the change to
     * optimize the view binding.
     *
     * This method delegates the removal to removeRange.
     *
     * @param selectedPositions list with item positions to remove
     * @param payload           any non-null user object to notify the parent (the payload will be
     * therefore passed to the bind method of the parent ViewHolder),
     * pass null to <u>not</u> notify the parent
     * @see removeItem
     * @see removeRange
     * @see removeAllSelectedItems
     * @see removeItems
     */
    fun removeItems(selectedPositions: List<Int>, payload: Any?) {
        Log.d("removeItems selectedPositions=$selectedPositions payload=$payload", TAG)
        var sortedPositions = selectedPositions
        if (sortedPositions.isEmpty()) return
        if (sortedPositions.size > 1) {
            // Reverse-sort the list, start from last position for efficiency
            sortedPositions = selectedPositions.sortedByDescending { it }
            Log.d("removeItems after reverse sort selectedPositions=$sortedPositions", TAG)
        }
        // Split the list in ranges
        var positionStart = 0
        var itemCount = 0
        var lastPosition = sortedPositions[0]
        multiRange = true
        for (position in sortedPositions) {//10 9 8 //5 4 //1
            if (lastPosition - itemCount == position) {//10-0==10  10-1==9  10-2==8  10-3==5 NO  //5-1=4  5-2==1 NO
                itemCount++             // 1  2  3  //2
                positionStart = position//10  9  8  //4
            } else {
                // Remove range
                if (itemCount > 0)
                    removeRange(positionStart, itemCount, payload)//8,3  //4,2
                lastPosition = position
                positionStart = lastPosition//5  //1
                itemCount = 1
            }
            // Request to collapse after the notification of remove range
            collapse(position)
        }
        multiRange = false
        // Remove last range
        if (itemCount > 0) {
            removeRange(positionStart, itemCount, payload)//1,1
        }
    }

    /**
     * Selectively removes all items of the type provided as parameter.
     *
     * **Tips:**
     * <br></br>- This method is opposite of [.clearAllBut].
     * <br></br>- View types of Scrollable Headers and Footers are ignored!
     *
     * @param viewTypes the viewTypes to remove
     * @see clear
     * @see clearAllBut
     * @see removeItem
     * @see removeItems
     * @see removeAllSelectedItems
     */
    fun removeItemsOfType(vararg viewTypes: Int) {
        val itemsToRemove = ArrayList<Int>()
        val startPosition = Math.max(0, mScrollableHeaders.size - 1)
        val endPosition = itemCount - mScrollableFooters.size - 1
        for (i in endPosition downTo startPosition) {
            if (viewTypes.contains(getItemViewType(i)))
                itemsToRemove.add(i)
        }
        this.removeItems(itemsToRemove)
    }

    /**
     * Same as [.removeRange], but in this case the parent will not be
     * notified about the change, if children are removed.
     *
     * @see clear
     * @see clearAllBut
     * @see removeItem
     * @see removeItems
     * @see removeItemsOfType
     * @see removeAllSelectedItems
     * @see removeRange
     */
    fun removeRange(
        @IntRange(from = 0) positionStart: Int,
        @IntRange(from = 0) itemCount: Int
    ) {
        this.removeRange(positionStart, itemCount, Payload.REM_SUB_ITEM)
    }

    /**
     * Removes a list of consecutive items and notify the change.
     *
     * If the item, resulting from the provided position:
     * - is <u>not expandable</u> with <u>no</u> parent, it is removed as usual.<br></br>
     * - is <u>not expandable</u> with a parent, it is removed only if the parent is expanded.<br></br>
     * - is <u>expandable</u> implementing [IExpandable], it is removed as usual, but
     * it will be collapsed if expanded.<br></br>
     * - has a [IHeader] item, the header will be automatically linked to the first item
     * after the range or can remain orphan.
     *
     * Optionally you can pass any payload to notify the <u>parent</u> or the <u>header</u>
     * about the change and optimize the view binding.
     *
     * @param positionStart the start position of the first item
     * @param itemCount     how many items should be removed
     * @param payload       any non-null user object to notify the parent (the payload will be
     * therefore passed to the bind method of the parent ViewHolder),
     * pass null to <u>not</u> notify the parent
     * @see clear
     * @see clearAllBut
     * @see removeItem
     * @see removeItems
     * @see removeRange
     * @see removeAllSelectedItems
     * @see setPermanentDelete
     * @see setRestoreSelectionOnUndo
     * @see getDeletedItems
     * @see getDeletedChildren
     * @see restoreDeletedItems
     * @see emptyBin
     */
    fun removeRange(
        @IntRange(from = 0) positionStart: Int,
        @IntRange(from = 0) itemCount: Int,
        payload: Any? = null
    ) {
        val initialCount = getItemCount()
        Log.d("removeRange positionStart=$positionStart itemCount=$itemCount", TAG)
        if (positionStart < 0 || positionStart + itemCount > initialCount) {
            Log.d("Cannot removeRange with positionStart OutOfBounds!", TAG)
            return
        } else if (itemCount == 0 || initialCount == 0) {
            Log.d("removeRange Nothing to delete!", TAG)
            return
        }

        var item: T? = null
        var parent: IExpandable<*, *>? = null
        for (position in positionStart until positionStart + itemCount) {
            item = getItem(positionStart) // We remove always at positionStart!
            if (item == null) continue

            if (!permanentDelete) {
                // When removing a range of children, parent is always the same :-)
                if (parent == null) parent = getExpandableOf(item)
                // Differentiate: (Expandable & NonExpandable with No parent) from (NonExpandable with a parent)
                if (parent == null) {
                    createRestoreItemInfo(positionStart, item)
                } else {
                    createRestoreSubItemInfo(parent, item)
                }
            }
            // Mark hidden the deleted item
            item.setHidden(true)
            // Unlink items that belongs to the removed header
            if (unlinkOnRemoveHeader && isHeader(item)) {
                val header = item as IHeader<*>?
                // If item is a Header, remove linkage from ALL Sectionable items if exist
                val sectionableList = getSectionItems(header!!)
                for (sectionable in sectionableList) {
                    sectionable.setHeader(null)
                    if (payload != null) {
                        notifyItemChanged(getGlobalPositionOf(sectionable as T), Payload.UNLINK)
                    }
                }
            }
            // Remove item from internal list
            listData.removeAt(positionStart)
            if (permanentDelete) {
                mOriginalList.remove(item)
            }
            removeSelection(position)
        }

        // Notify range removal
        notifyItemRangeRemoved(positionStart, itemCount)

        // Update content of the header linked to first item of the range
        if (item != null && parent != null) {
            val headerPosition = getGlobalPositionOf(getHeaderOf(item) as T)
            if (headerPosition >= 0) {
                notifyItemChanged(headerPosition, payload)
            }

            val parentPosition = getGlobalPositionOf(parent as T)
            if (parentPosition >= 0 && parentPosition != headerPosition) {
                notifyItemChanged(parentPosition, payload)
            }
        }

        // Update empty view
        if (!multiRange && initialCount > 0 && getItemCount() == 0)
            onUpdateListener?.onUpdateEmptyView(this, getMainItemCount())
    }


    /**
     * Convenience method to remove all items that are currently selected.
     *
     * Optionally, the parent item can be notified about the change if a child is removed,
     * by providing any non-null payload.
     *
     * @param payload any non-null user object to notify the parent (the payload will be
     * therefore passed to the bind method of the parent ViewHolder),
     * pass null to <u>not</u> notify the parent
     * @see clear
     * @see clearAllBut
     * @see removeItem
     * @see removeItems
     * @see removeRange
     * @see removeAllSelectedItems
     */
    fun removeAllSelectedItems(payload: Any? = null) {
        this.removeItems(getSelectedPositions(), payload)
    }


    /*----------------------*/
    /* UNDO/RESTORE METHODS */
    /*----------------------*/

    /**
     * Returns if items will be deleted immediately when deletion is requested.
     *
     * Default value is `true` (Undo mechanism is disabled).
     *
     * @return true if the items are deleted immediately, false if items are retained for an
     * eventual restoration
     */
    fun isPermanentDelete(): Boolean {
        return permanentDelete
    }

    /**
     * Sets if the deleted items should be deleted immediately or if Adapter should cache them to
     * restore them when requested by the user.
     *
     * Default value is `true` (Undo mechanism is disabled).
     *
     * @param permanentDelete true to delete items forever, false to use the cache for Undo feature
     * @return this Adapter, so the call can be chained
     */
    fun setPermanentDelete(permanentDelete: Boolean): FlexibleAdapter<T> {
        Log.d("Set permanentDelete=$permanentDelete", TAG)
        this.permanentDelete = permanentDelete
        return this
    }

    /**
     * Returns the current configuration to restore selections on Undo.
     *
     * Default value is `false` (selection is NOT restored).
     *
     * @return true if selection will be restored, false otherwise
     * @see setRestoreSelectionOnUndo
     */
    fun isRestoreWithSelection(): Boolean {
        return restoreSelection
    }

    /**
     * Gives the possibility to restore the selection on Undo, when [.restoreDeletedItems]
     * is called.
     *
     * To use in combination with `ActionMode` in order to not disable it.
     * Default value is `false` (selection is NOT restored).
     *
     * @param restoreSelection true to have restored items still selected, false to empty selections
     * @return this Adapter, so the call can be chained
     */
    fun setRestoreSelectionOnUndo(restoreSelection: Boolean): FlexibleAdapter<T> {
        Log.d("Set restoreSelectionOnUndo=$restoreSelection", TAG)
        this.restoreSelection = restoreSelection
        return this
    }

    /**
     * Restore items just removed.
     *
     * **Tip:** If filter is active, only items that match that filter will be shown(restored).
     *
     * @see setRestoreSelectionOnUndo
     */
    fun restoreDeletedItems() {
        multiRange = true
        val initialCount = itemCount
        // Selection coherence: start from a clean situation
        if (getSelectedItemCount() > 0) clearSelection()
        // Start from latest item deleted, since others could rely on it
        for (i in mRestoreList.indices.reversed()) {
            adjustSelected = false
            val restoreInfo = mRestoreList[i]

            if (restoreInfo.relativePosition >= 0) {
                // Restore child
                Log.d("Restore SubItem $restoreInfo", TAG)
                addSubItem(
                    restoreInfo.getRestorePosition(true), restoreInfo.relativePosition,
                    restoreInfo.item, false, Payload.UNDO
                )
            } else {
                // Restore parent or simple item
                Log.d("Restore Item $restoreInfo", TAG)
                addItem(restoreInfo.getRestorePosition(false), restoreInfo.item)
            }
            // Item is again visible
            restoreInfo.item.setHidden(false)
            // Restore header linkage
            if (unlinkOnRemoveHeader && isHeader(restoreInfo.item)) {
                val header = restoreInfo.item as IHeader<*>
                val items = getSectionItems(header)
                for (sectionable in items) {
                    linkHeaderTo(sectionable as T, header, Payload.LINK)
                }
            }
        }
        // Restore selection if requested, before emptyBin
        if (restoreSelection && !mRestoreList.isEmpty()) {
            if (isExpandableItem(mRestoreList[0].item) || getExpandableOf(mRestoreList[0].item) == null) {
                parentSelected = true
            } else {
                childSelected = true
            }
            for (restoreInfo in mRestoreList) {
                if (restoreInfo.item.isSelectable()) {
                    addSelection(getGlobalPositionOf(restoreInfo.item))
                }
            }
            Log.d("Selected positions after restore ${getSelectedPositions()}", TAG)
        }
        // Call listener to update EmptyView
        multiRange = false
        if (initialCount == 0 && itemCount > 0)
            onUpdateListener?.onUpdateEmptyView(this, getMainItemCount())

        emptyBin()
    }

    /**
     * Performs removal confirmation and cleans memory by synchronizing the internal list from deletion.
     *
     * **Note:** This method is automatically called after timer is over in UndoHelper.
     *
     */
    fun confirmDeletion() {
        Log.d("confirmDeletion!", TAG)
        mOriginalList.removeAll(getDeletedItems())
        emptyBin()
    }

    /**
     * Cleans memory from items just removed.
     *
     * **Note:** This method is automatically called after timer is over in UndoHelper
     * and after a restoration.
     *
     */
    @Synchronized
    fun emptyBin() {
        Log.d("emptyBin!", TAG)
        mRestoreList.clear()
        mUndoPositions.clear()
    }

    /**
     * @return true if the restore list is not empty, false otherwise
     */
    @Synchronized
    fun isRestoreInTime(): Boolean {
        return mRestoreList.isNotEmpty()
    }

    /**
     * @return the list of deleted items
     */
    fun getDeletedItems(): List<T> {
        val deletedItems = ArrayList<T>()
        for (restoreInfo in mRestoreList) {
            deletedItems.add(restoreInfo.item)
        }
        return deletedItems
    }

    /**
     * @return the list of positions to undo
     */
    fun getUndoPositions(): List<Int> {
        return this.mUndoPositions
    }

    /**
     * @param undoPositions the positions to Undo with UndoHelper when `Action.UPDATE`.
     */
    fun saveUndoPositions(undoPositions: List<Int>) {
        mUndoPositions.addAll(undoPositions)
    }

    /**
     * Retrieves the expandable of the deleted child.
     *
     * @param child the deleted child
     * @return the expandable(parent) of this child, or null if no parent found.
     */
    fun getExpandableOfDeletedChild(child: T): IExpandable<*, *>? {
        for (restoreInfo in mRestoreList) {
            if (restoreInfo.item == child && isExpandableItem(restoreInfo.refItem))
                return restoreInfo.refItem as IExpandable<*, *>
        }
        return null
    }

    /**
     * Retrieves only the deleted children of the specified parent.
     *
     * @param expandable the parent item
     * @return the list of deleted children
     */
    fun getDeletedChildren(expandable: IExpandable<*, *>): List<T> {
        val deletedChild = mutableListOf<T>()
        for (restoreInfo in mRestoreList) {
            if (restoreInfo.refItem == expandable && restoreInfo.relativePosition >= 0)
                deletedChild.add(restoreInfo.item)
        }
        return deletedChild
    }

    /**
     * Retrieves all the original children of the specified parent, filtering out all the
     * deleted children if any.
     *
     * @param expandable the parent item
     * @return a non-null list of the original children minus the deleted children if some are
     * pending removal.
     */
    fun getCurrentChildren(expandable: IExpandable<*, *>?): List<T> {
        // Check item and subItems existence
        val resultList = mutableListOf<T>()
        if (expandable == null || !hasSubItems(expandable))
            return resultList

        // Take a copy of the subItems list
        resultList.addAll(expandable.getSubItems() as List<T>)
        // Remove all children pending removal
        if (mRestoreList.isNotEmpty()) {
            resultList.removeAll(getDeletedChildren(expandable))
        }
        return resultList
    }


    /*----------------*/
    /* FILTER METHODS */
    /*----------------*/

    /**
     * @return true if the current filter is not `null`
     */
    fun hasFilter(): Boolean {
        return mFilterEntity.isNotEmpty()
    }

    /**
     * Checks if the filter is changed.
     *
     * @param constraint the new filter entity
     * @return true if the old filter is different than the new one, false if not changed
     */
    fun hasNewFilter(constraint: String): Boolean {
        return mOldFilterEntity.toString().equals(constraint, ignoreCase = true).not()
    }

    /**
     * Sets the new filter entity.
     *
     * **Note:**
     *  * In case of free text (`String`), filter is automatically **trimmed**
     * and **lowercase**.
     *
     *
     * **Tip:** You can highlight filtered Text or Words using `FlexibleUtils`
     * from UI extension:
     *  * `FlexibleUtils#highlightText(TextView, String, String)`
     *  * `FlexibleUtils#highlightWords(TextView, String, String)`
     *
     * @param filter the new filter entity for the items
     */
    fun setFilter(filter: String = "") {
        mFilterEntity = StringBuilder(filter.trim { it <= ' ' }.lowercase())
    }

    /**
     * Gets the current filter.
     *
     * @return the current filter entity
     */
    fun getFilter(): String {
        return mFilterEntity.toString()
    }

    /**
     * With this setting, we can skip the check of the implemented method
     * [IFlexible.shouldNotifyChange].
     *
     * By setting false [all] items will be skipped by an update.
     * Default value is `true` (items will always be notified of a change).
     *
     * @param notifyChange true to trigger [.notifyItemChanged],
     * false to not update the items' content.
     * @return this Adapter, so the call can be chained
     */
    fun setNotifyChangeOfUnfilteredItems(notifyChange: Boolean): FlexibleAdapter<T> {
        Log.d("Set notifyChangeOfUnfilteredItems=$notifyChange", TAG)
        this.notifyChangeOfUnfilteredItems = notifyChange
        return this
    }

    /**
     * This method performs a further step to nicely animate the moved items. When false, the
     * items are not moved but removed, to be added at the correct position.
     *
     * The process is very slow on big list of the order of ~3-5000 items and higher,
     * due to the calculation of the correct position for each item to be shifted.
     * Use with caution!
     * The slowness is higher when the filter is cleared out.
     *
     * Default value is `false`.
     *
     * @param notifyMove true to animate move changes after filtering or update data set,
     * false otherwise
     * @return this Adapter, so the call can be chained
     */
    fun setNotifyMoveOfFilteredItems(notifyMove: Boolean): FlexibleAdapter<T> {
        Log.d("Set notifyMoveOfFilteredItems=$notifyMove", TAG)
        this.notifyMoveOfFilteredItems = notifyMove
        return this
    }

    /**
     *
     * @param delay any non-negative delay
     */
    fun filterItems(@IntRange(from = 0) delay: Long = 0) {
        if (mOriginalList.isEmpty()) {
            mOriginalList = listData.toMutableList()
        }
        filterItems(mOriginalList, delay)
    }

    /**
     * **WATCH OUT! ADAPTER ALREADY CREATES A <u>COPY</u> OF THE PROVIDED LIST**: due to internal
     * mechanism, items are removed and/or added in order to animate items in the final list.
     *
     * Same as [.filterItems], but with a delay in the execution, useful to grab
     * more characters from user before starting the search.
     *
     * @param unfilteredItems the list to filter
     * @param delay           any non-negative delay
     */
    fun filterItems(unfilteredItems: List<T>, @IntRange(from = 0) delay: Long = 0) {
        //Make longer the timer for new coming deleted items
        mFilterJob?.cancel()
        mFilterJob = coroutineScope.launch(Dispatchers.Main) {
            if (delay > 0) {
                delay(delay)
            }
            filterAsync(ACTION_FILTER, unfilteredItems)
        }
    }


    @Synchronized
    private fun filterItemsAsync(unfilteredItems: List<T>) {
        Log.d("filterItems with filterEntity=$mFilterEntity", TAG)
        val filteredResultItems = mutableListOf<T>()
        val keyFilter = mFilterEntity.toString()
        filtering = true //Enable flag
        if (hasFilter() && hasNewFilter(keyFilter)) { //skip when filter is unchanged
            for (item in unfilteredItems) {
                if (mFilterJob != null && true == mFilterJob?.isCancelled) return
                // Filter normal AND expandable objects
                filterObject(item, filteredResultItems)
            }
        } else if (hasNewFilter(keyFilter)) {
            filteredResultItems.addAll(unfilteredItems) //original items with no filter
            resetFilterFlags(filteredResultItems) //recursive reset
            mExpandedFilterFlags = null
            if (mOriginalList.isEmpty()) restoreScrollableHeadersAndFooters(filteredResultItems)
            mOriginalList.clear()
        }


        // Animate search results only in case of new Filter
        if (hasNewFilter(keyFilter)) {
            mOldFilterEntity = StringBuilder(keyFilter)
            toggleAnimate(filteredResultItems, Payload.FILTER)
        }

        // Reset flag
        filtering = false
    }

    /**
     * @return true if the filter is currently running, false otherwise.
     */
    fun isFiltering(): Boolean {
        return filtering
    }


    /**
     * This method is a wrapper filter for expandable items.<br></br>
     * It performs filtering on the subItems returning true, if the any child should be in the
     * filtered collection.
     *
     * If the provided item is not an expandable it will be filtered as usual by
     * [.filterObject].
     *
     * @param item the object with subItems to be inspected
     * @return true, if the object should be in the filteredResult, false otherwise
     */
    private fun filterObject(item: T, values: MutableList<T>): Boolean {
        // Stop filter task if cancelled
        if (mFilterJob != null && true == mFilterJob?.isCancelled) return false
        // Skip already filtered items (it happens when internal originalList)
        if ((isScrollableHeaderOrFooter(item) || values.contains(item))) {
            return false
        }
        // Start to compose the filteredItems to maintain the order of addition
        // It will be discarded if no subItem will be filtered
        val filteredResultItems = mutableListOf(item)
        // Filter subItems
        var filtered = filterExpandableObject(item, filteredResultItems)
        // If no subItem was filtered, fallback to Normal filter
        if (!filtered) {
            filtered = filterObject(item, getFilter())
        }
        if (filtered) {
            // Check if header has to be added too
            getHeaderOf(item)?.also {
                if (headersShown && hasHeader(item) && !values.contains(it as T)) {
                    it.setHidden(false)
                    values.add(it)
                }
            }

            values.addAll(filteredResultItems)
        }
        item.setHidden(!filtered)
        return filtered
    }


    private fun filterExpandableObject(item: T, values: MutableList<T>): Boolean {
        var filtered = false
        // Is item an expandable?
        if (isExpandableItem(item)) {
            val expandable = item as IExpandable<*, *>
            // Save which expandable was originally expanded before filtering it out
            if (expandable.isExpanded()) {
                if (mExpandedFilterFlags == null) {
                    mExpandedFilterFlags = hashSetOf()
                }
                mExpandedFilterFlags?.add(expandable)
            }
            // SubItems scan filter
            for (subItem in getCurrentChildren(expandable)) {
                // Recursive filter for subExpandable
                if (subItem is IExpandable<*, *> && filterObject(subItem, values)) {
                    filtered = true
                } else {
                    // Use normal filter for normal subItem
                    subItem.setHidden(!filterObject(subItem, getFilter()))
                    if (!subItem.isHidden()) {
                        filtered = true
                        values.add(subItem)
                    }
                }
            }
            // Expand if filter found text in subItems
            expandable.setExpanded(filtered)
        }
        return filtered
    }


    /**
     * This method checks if the provided object is a type of [IFilterable] interface,
     * if yes, performs the filter on the implemented method [IFilterable.filter].
     *
     * **Note:**
     * <br></br>- The item will be collected if the implemented method returns true.
     * <br></br>- `IExpandable` items are automatically picked up and displayed if at least a
     * child is collected by the current filter. You DON'T NEED to implement the scan for the
     * children: this is already done :-)
     * <br></br>- If you don't want to implement the `IFilterable` interface on each item, then
     * you can override this method to have another filter logic!
     *
     * @param item       the object to be inspected
     * @param constraint constraint, that the object has to fulfil
     * @return true, if the object returns true as well, and so if it should be in the
     * filteredResult, false otherwise
     */
    private fun filterObject(item: T, constraint: String): Boolean {
        return (item as? IFilterable)?.filter(constraint) ?: false
    }


    /**
     * Clears flags after filter is cleared out for Expandable filteredItems and sub items.
     * Also restore headers visibility.
     */
    private fun resetFilterFlags(filteredItems: MutableList<T>?) {
        if (filteredItems == null) return
        var sameHeader: IHeader<*>? = null
        // Reset flags for all items!
        var i = 0
        while (i < filteredItems.size) {
            val item = filteredItems[i]
            item.setHidden(false)
            if (isExpandableItem(item)) {
                val expandable = item as IExpandable<*, *>
                // Reset expanded flag
                expandable.setExpanded(
                    mExpandedFilterFlags != null && true == mExpandedFilterFlags?.contains(
                        expandable
                    )
                )
                if (hasSubItems(expandable)) {
                    val subItems = expandable.getSubItems()
                    // Reset subItem hidden flag2
                    for (subItem in subItems) {
                        (subItem as IFlexible<out FlexibleExpandableViewHolder>).setHidden(false)
                        if (subItem is IExpandable<*, *>) {
                            subItem.setExpanded(false)
                            resetFilterFlags(
                                subItem.getSubItems().toMutableList() as MutableList<T>
                            )
                        }
                    }
                    // Show subItems for expanded items
                    if (expandable.isExpanded() && mOriginalList.isEmpty()) {
                        if (i < filteredItems.size)
                            filteredItems.addAll(i + 1, subItems as List<T>)
                        else
                            filteredItems.addAll(subItems as List<T>)
                        i += subItems.size
                    }
                }
            }

            // Restore headers visibility
            if (headersShown && mOriginalList.isEmpty()) {
                getHeaderOf(item)?.also {
                    if (it != sameHeader && !isExpandableItem(it as T)) {
                        it.setHidden(false)
                        sameHeader = it
                        filteredItems.add(i, it as T)
                        i++
                    }
                }
            }

            i++
        }
    }

    /**
     * Tunes the limit after the which the synchronization animations, occurred during
     * updateDataSet and filter operations, are skipped and [.notifyDataSetChanged]
     * will be called instead.
     *
     * Default value is [ANIMATE_TO_LIMIT] items, number new items.
     *
     * @param limit the number of new items that, when reached, will skip synchronization animations
     * @return this Adapter, so the call can be chained
     */
    fun setAnimateToLimit(limit: Int): FlexibleAdapter<T> {
        Log.d("Set animateToLimit=$limit", TAG)
        mAnimateToLimit = limit
        return this
    }

    /*-------------------------*/
    /* ANIMATE CHANGES METHODS */
    /*-------------------------*/

    /**
     * @return true to calculate animation changes with DiffUtil, default true
     */
    fun isAnimateChangesWithDiffUtil(): Boolean {
        return useDiffUtil
    }

    /**
     * Whether use [DiffUtil] to calculate the changes between 2 lists after Update or
     * Filter operations. If disabled, the advanced default calculation will be used instead.
     *
     * A time, to compare the 2 different approaches, is calculated and displayed in the log.
     * To see the logs call [.enableLogs] before creating the Adapter instance!
     * Default value is `false` (default use diffUtils is used).
     *
     * @param useDiffUtil true to switch the calculation and use DiffUtil, false to use the default
     * calculation.
     * @return this Adapter, so the call can be chained
     */
    fun setAnimateChangesWithDiffUtil(useDiffUtil: Boolean): FlexibleAdapter<T> {
        this.useDiffUtil = useDiffUtil
        return this
    }


    /**
     * Sets a custom implementation of [DiffUtilCallback] for the DiffUtil.
     *
     * @param diffUtilCallback the custom callback that DiffUtil will call
     * @return this Adapter, so the call can be chained
     */
    fun <D : FlexibleDiffCallback<T>> setDiffUtilCallback(diffUtilCallback: D): FlexibleAdapter<T> {
        this.useDiffUtil = true
        this.diffUtilCallback = diffUtilCallback
        return this
    }

    @Synchronized
    private fun toggleAnimate(newItems: List<T>, payloadChange: Payload) {
        if (useDiffUtil) {
            animateDiff(newItems)
        } else {
            animateTo(newItems, payloadChange)
        }
    }

    /**
     * Animate the synchronization use diffUtilCallback
     * Used if [useDiffUtil] is true. default is to use
     * @param newItems
     * @see setAnimateChangesWithDiffUtil
     * @see setDiffUtilCallback
     *
     */

    @Synchronized
    private fun animateDiff(newItems: List<T>) {
        Log.d(
            "Animate changes with DiffUtils! oldSize=" + itemCount + " newSize=" + newItems.size,
            TAG
        )
        // create default instance if not set diffUtilCallBack
        if (diffUtilCallback == null) {
            diffUtilCallback = FlexibleDiffCallback(listData, newItems)
        }
        diffUtilCallback?.also {
            it.setItems(listData, newItems)
            diffResult = DiffUtil.calculateDiff(it, notifyMoveOfFilteredItems)
        }
    }

    /**
     * Animate the synchronization between the old list and the new list.
     *
     * Used by filter and updateDataSet.
     * **Note:** The animations are skipped in favor of [.notifyDataSetChanged]
     * when the number of items reaches the limit. See [.setAnimateToLimit].
     *
     * **Note:** In case the animations are performed, unchanged items will be notified if
     * `notifyChangeOfUnfilteredItems` is set true, a CHANGE payload will be set.
     *
     * @param newItems the new list containing the new items
     */
    @Synchronized
    private fun animateTo(newItems: List<T>, payloadChange: Payload) {
        mNotifications = mutableListOf()
        if (newItems.size <= mAnimateToLimit) {
            Log.d(
                "Animate changes! oldSize=$itemCount newSize=${newItems.size} limit=$mAnimateToLimit",
                TAG
            )
            mTempItems = listData.map { it }.toMutableList()
            applyAndAnimateRemovals(mTempItems as MutableList<T>, newItems)
            applyAndAnimateAdditions(mTempItems as MutableList<T>, newItems)
            if (notifyMoveOfFilteredItems) applyAndAnimateMovedItems(
                mTempItems as MutableList<T>,
                newItems
            )
        } else {
            Log.d(
                "NotifyDataSetChanged! oldSize=$itemCount newSize=${newItems.size} limit=$mAnimateToLimit",
                TAG
            )
            mTempItems = newItems.map { it }.toList()
            mNotifications?.add(Notification(operation = NONE))
        }
        // Execute All notifications if filter was Synchronous!
        if (mFilterJob == null) executeNotifications(payloadChange)
    }


    /**
     * Calculates the modifications for items to rebound.
     *
     * @return A Map with the unfilteredItems items to rebound and their index
     */
    private fun applyModifications(from: List<T>, newItems: List<T>): Map<T, Int>? {
        if (notifyChangeOfUnfilteredItems) {
            // Using Hash for performance
            mHashItems = HashSet(from)
            val unfilteredItems = HashMap<T, Int>()
            for (i in newItems.indices) {
                if (mFilterJob != null && true == mFilterJob?.isCancelled) break
                val item = newItems[i]
                // Save the index of this new item
                if (true == mHashItems?.contains(item)) unfilteredItems[item] = i
            }
            return unfilteredItems
        }
        return null
    }

    /**
     * Find out all removed items and animate them, also update existent positions with newItems.
     */
    private fun applyAndAnimateRemovals(from: MutableList<T>, newItems: List<T>) {
        // This avoids the call indexOf() later on: newItems.get(unfilteredItems.indexOf(item)));
        val unfilteredItems = applyModifications(from, newItems)

        // Using Hash for performance
        mHashItems = HashSet(newItems)
        var out = 0
        var mod = 0
        for (i in from.indices.reversed()) {
            if (mFilterJob != null && true == mFilterJob?.isCancelled) return
            val item = from[i]
            if (false == mHashItems?.contains(item)) {
                Log.d("calculateRemovals remove position=$i item=$item", TAG)
                from.removeAt(i)
                mNotifications?.add(Notification(-1, i, REMOVE))
                out++
            } else if (notifyChangeOfUnfilteredItems) {
                assert(unfilteredItems != null)
                val newItem = newItems[unfilteredItems?.get(item) ?: 0]
                // Check whether the old content should be updated with the new one
                // Always true in case filter is active
                if (isFiltering() || item.shouldNotifyChange(newItem)) {
                    from[i] = newItem
                    mNotifications?.add(Notification(-1, i, CHANGE))
                    mod++
                }
            }
        }
        mHashItems = null
        Log.d("calculateModifications total mod=$mod", TAG)
        Log.d("calculateRemovals total out=$out", TAG)
    }


    /**
     * Find out all added items and animate them.
     *
     */
    private fun applyAndAnimateAdditions(from: MutableList<T>, newItems: List<T>) {
        // Using Hash for performance
        mHashItems = HashSet(from)
        var count = 0
        for (position in newItems.indices) {
            if (mFilterJob != null && true == mFilterJob?.isCancelled) return
            val item = newItems[position]
            if (false == mHashItems?.contains(item)) {
                Log.d("calculateAdditions add position=$position item=$item", TAG)
                if (notifyMoveOfFilteredItems) {
                    // We add always at the end to animate moved items at the missing position
                    from.add(item)
                    mNotifications?.add(Notification(-1, from.size, ADD))
                } else {
                    // Filtering issue during delete search query (make sure position is in bounds)
                    if (position < from.size) {
                        from.add(position, item)
                    } else {
                        from.add(item)
                    }
                    mNotifications?.add(Notification(-1, position, ADD))
                }
                count++
            }
        }
        mHashItems = null
        Log.d("calculateAdditions total new=$count", TAG)
    }

    /**
     * Find out all moved items and animate them.
     *
     * This method is very slow on list bigger than ~3000 items. Use with caution!
     *
     */
    private fun applyAndAnimateMovedItems(from: MutableList<T>, newItems: List<T>) {
        var move = 0
        for (toPosition in newItems.indices.reversed()) {
            if (mFilterJob != null && true == mFilterJob?.isCancelled) return
            val item = newItems[toPosition]
            val fromPosition = from.indexOf(item)
            if (fromPosition >= 0 && fromPosition != toPosition) {
                Log.d("calculateMovedItems fromPosition=$fromPosition toPosition=$toPosition", TAG)
                val movedItem = from.removeAt(fromPosition)
                if (toPosition < from.size)
                    from.add(toPosition, movedItem)
                else
                    from.add(movedItem)
                mNotifications?.add(Notification(fromPosition, toPosition, MOVE))
                move++
            }
        }
        Log.d("calculateMovedItems total move=$move", TAG)
    }


    @Synchronized
    private fun executeNotifications(payloadChange: Payload) {
        if (diffResult != null) {
            Log.d("Dispatching notifications use Diff", TAG)
            listData = diffUtilCallback?.newList?.toMutableList()
                ?: mutableListOf()// Update mItems in the UI Thread
            diffResult?.dispatchUpdatesTo(this)
            diffResult = null
        } else {
            Log.d("Performing $mNotifications.size notifications", TAG)
            listData = mTempItems.toMutableList()     // Update mItems in the UI Thread
            setScrollAnimate(false) // Disable scroll animation
            mNotifications?.forEach {
                when (it.operation) {
                    ADD -> notifyItemInserted(it.toPosition)
                    CHANGE -> notifyItemChanged(it.toPosition, payloadChange)
                    REMOVE -> notifyItemRemoved(it.toPosition)
                    MOVE -> notifyItemMoved(it.fromPosition, it.toPosition)
                    else -> {
                        Log.d("notifyDataSetChanged!", TAG)
                        notifyDataSetChanged()
                    }
                }
            }
            // reset
            mTempItems = listOf()
            mNotifications = null
            setScrollAnimate(true)
        }
        endTimeFiltered = System.currentTimeMillis() - startTimeFilter
        Log.d("Animate changes DONE in $endTimeFiltered ms", TAG)
    }

    /**
     * @return the time (in ms) of the last update or filter operation.
     */
    fun getTimeFiltered(): Long {
        return endTimeFiltered
    }


    /*---------------*/
    /* TOUCH METHODS */
    /*---------------*/

    private fun initializeItemTouchHelper() {
        if (mItemTouchHelper == null) {
            if (!recyclerViewHasInitialized()) {
                throw IllegalStateException("RecyclerView cannot be null. Enabling LongPressDrag or Swipe must be done after the Adapter has been attached to the RecyclerView.")
            }

            if (mItemTouchHelperCallback == null) {
                mItemTouchHelperCallback = ItemTouchHelperCallback(this)
                Log.d("Initialized default ItemTouchHelperCallback", TAG)
            }
            mItemTouchHelperCallback?.also {
                mItemTouchHelper = ItemTouchHelper(it)
                mItemTouchHelper?.attachToRecyclerView(mRecyclerView)
            }
        }
    }

    /**
     * Used by [FlexibleViewHolder.onTouch]
     * to start Drag or Swipe when HandleView is touched.
     *
     * @return the ItemTouchHelper instance already initialized.
     */
    fun getItemTouchHelper(): ItemTouchHelper? {
        initializeItemTouchHelper()
        return mItemTouchHelper
    }

    /**
     * Returns the customization of the ItemTouchHelperCallback or the default if it wasn't set
     * before.
     *
     * @return the ItemTouchHelperCallback instance already initialized
     * @see setItemTouchHelperCallback
     */
    fun getItemTouchHelperCallback(): ItemTouchHelperCallback? {
        initializeItemTouchHelper()
        return mItemTouchHelperCallback
    }


    /**
     * Sets a custom callback implementation for item touch.
     *
     * If called, Helper will be reinitialized.
     * If not called, the default Helper will be used.
     *
     * @param itemTouchHelperCallback the custom callback implementation for item touch
     * @return this Adapter, so the call can be chained
     */
    fun setItemTouchHelperCallback(callback: ItemTouchHelperCallback): FlexibleAdapter<T> {
        mItemTouchHelperCallback = callback
        mItemTouchHelper = null
        initializeItemTouchHelper()
        Log.d("Initialized custom ItemTouchHelperCallback", TAG)
        return this
    }

    /**
     * Returns whether ItemTouchHelper should start a drag and drop operation if an item is
     * long pressed.
     *
     *
     * Default value is `false`.
     *
     * @return true if ItemTouchHelper should start dragging an item when it is long pressed,
     * false otherwise. Default value is `false`.
     * @see setLongPressDragEnabled
     */
    fun isLongPressDragEnabled(): Boolean {
        return mItemTouchHelperCallback != null && mItemTouchHelperCallback?.isLongPressDragEnabled ?: false
    }


    /**
     * Enables / Disables the drag of the item when long-press the entire itemView.
     *
     * **Note:** This will skip LongClick on the view in order to handle the LongPress,
     * however the LongClick listener will be called if necessary in the new
     * [FlexibleViewHolder.onActionStateChanged].
     * Requires the Adapter being attached to the RecyclerView.
     * Default value is `false`.
     *
     * @param longPressDragEnabled true to activate, false otherwise
     * @return this Adapter, so the call can be chained
     */
    fun setLongPressDragEnabled(longPressDragEnabled: Boolean): FlexibleAdapter<T> {
        initializeItemTouchHelper()
        Log.d("Set longPressDragEnabled=$longPressDragEnabled", TAG)
        mItemTouchHelperCallback?.longPressDragEnabled = longPressDragEnabled
        return this
    }

    /**
     * Returns whether ItemTouchHelper should start a drag and drop operation by touching its
     * handle.
     *
     * Default value is `false`.
     * To use, it is sufficient to set the HandleView by calling
     * [FlexibleViewHolder.setDragHandleView].
     *
     * @return true if active, false otherwise
     * @see setHandleDragEnabled
     */
    fun isHandleDragEnabled(): Boolean {
        return mItemTouchHelperCallback != null && mItemTouchHelperCallback?.handleDragEnabled ?: false
    }

    /**
     * Enables / Disables the drag of the item with a handle view.
     * Requires the Adapter being attached to the RecyclerView.
     *
     * Default value is `false`.
     *
     * @param handleDragEnabled true to activate, false otherwise
     * @return this Adapter, so the call can be chained
     */
    fun setHandleDragEnabled(handleDragEnabled: Boolean): FlexibleAdapter<T> {
        initializeItemTouchHelper()
        Log.d("Set handleDragEnabled=$handleDragEnabled", TAG)
        this.mItemTouchHelperCallback?.handleDragEnabled = handleDragEnabled
        return this
    }

    /**
     * Returns whether ItemTouchHelper should start a swipe operation if a pointer is swiped
     * over the View.
     *
     * Default value is `false`.
     *
     * @return true if ItemTouchHelper should start swiping an item when user swipes a pointer
     * over the View, false otherwise. Default value is `false`.
     */
    fun isSwipeEnabled(): Boolean {
        return mItemTouchHelperCallback != null && mItemTouchHelperCallback?.isItemViewSwipeEnabled ?: false
    }

    /**
     * Enables full the swipe of the items.
     *
     * **Note:**
     *  * Requires the Adapter being attached to the RecyclerView.
     *  * Must override `getFrontView` and at least a rear View: `getRearLeftView` and/or
     * `getRearRightView` in the `FlexibleViewHolder`.
     * Default value is `false`.
     *
     * @param swipeEnabled true to activate, false otherwise
     * @return this Adapter, so the call can be chained
     */
    fun setSwipeEnabled(swipeEnabled: Boolean): FlexibleAdapter<T> {
        Log.d("Set swipeEnabled=$swipeEnabled", TAG)
        initializeItemTouchHelper()
        mItemTouchHelperCallback?.swipeEnabled = swipeEnabled
        return this
    }


    /**
     * Moves the item placed at position `fromPosition` to the position
     * `toPosition`.
     * - Selection of moved element is preserved.
     * - If item is an expandable, it is collapsed and then expanded at the new position.
     *
     * @param fromPosition previous position of the item
     * @param toPosition   new position of the item
     * @param payload      allows to update the content of the item just moved
     */
    fun moveItem(fromPosition: Int, toPosition: Int, payload: Any? = Payload.MOVE) {
        Log.d("moveItem fromPosition=$fromPosition toPosition=$toPosition", TAG)
        // Preserve selection
        if (isSelected(fromPosition)) {
            removeSelection(fromPosition)
            addSelection(toPosition)
        }
        val item = getItem(fromPosition)
        // Preserve expanded status and Collapse expandable
        val expanded = isExpanded(item)
        if (expanded) collapse(toPosition)
        // Move item!
        item?.also {
            listData.removeAt(fromPosition)
            performInsert(toPosition, listOf(item), false)
            notifyItemMoved(fromPosition, toPosition)
            if (payload != null) notifyItemChanged(toPosition, payload)
            // Eventually display the new Header if the moved item has brand new header
            if (headersShown) showHeaderOf(toPosition, item, false)
        }
        // Restore original expanded status
        if (expanded) expand(toPosition)
    }


    /**
     * Swaps the elements of list at indices fromPosition and toPosition and notify the change.
     *
     * Selection of swiped elements is automatically updated.
     *
     * @param fromPosition previous position of the item.
     * @param toPosition   new position of the item.
     */
    fun swapItems(list: List<T>, fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || fromPosition >= itemCount ||
            toPosition < 0 || toPosition >= itemCount
        ) {
            return
        }
        Log.d(
            "swapItems from=$fromPosition [selected? ${isSelected(fromPosition)}] to=$toPosition [selected? ${
                isSelected(
                    toPosition
                )
            }]", TAG
        )

        // Collapse expandable before swapping (otherwise items are mixed badly)
        if (fromPosition < toPosition && isExpandableItem(getItem(fromPosition)) && isExpanded(
                toPosition
            )
        ) {
            collapse(toPosition)
        }

        // Perform item swap (for all LayoutManagers)
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                Log.d("swapItems from=$i to=${i + 1}", TAG)
                Collections.swap(list, i, i + 1)
                swapSelection(i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Log.d("swapItems from=$i to=${i - 1}", TAG)
                Collections.swap(list, i, i - 1)
                swapSelection(i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)

        // Header swap linkage
        if (headersShown) {
            // Situation AFTER items have been swapped, items are inverted!
            val fromItem = getItem(toPosition)
            val toItem = getItem(fromPosition)
            val oldPosition: Int
            val newPosition: Int
            if (toItem is IHeader<*> && fromItem is IHeader<*>) {
                val header = if (fromPosition < toPosition) fromItem else toItem
                val items = getSectionItems(header)
                for (sectionable in items) {
                    linkHeaderTo(sectionable as T, header, Payload.LINK)
                }
            } else if (toItem is IHeader<*>) {
                // A Header is being swapped up
                // Else a Header is being swapped down
                oldPosition = if (fromPosition < toPosition) toPosition + 1 else toPosition
                newPosition = if (fromPosition < toPosition) toPosition else fromPosition + 1
                // Swap header linkage
                linkHeaderTo(getItem(oldPosition), getSectionHeader(oldPosition), Payload.LINK)
                linkHeaderTo(getItem(newPosition), toItem, Payload.LINK)
            } else if (fromItem is IHeader<*>) {
                // A Header is being dragged down
                // Else a Header is being dragged up
                oldPosition = if (fromPosition < toPosition) fromPosition else fromPosition + 1
                newPosition = if (fromPosition < toPosition) toPosition + 1 else fromPosition
                // Swap header linkage
                linkHeaderTo(getItem(oldPosition), getSectionHeader(oldPosition), Payload.LINK)
                linkHeaderTo(getItem(newPosition), fromItem, Payload.LINK)
            } else {
                // A Header receives the toItem
                // Else a Header receives the fromItem
                oldPosition = if (fromPosition < toPosition) toPosition else fromPosition
                newPosition = if (fromPosition < toPosition) fromPosition else toPosition
                // Swap header linkage
                getItem(oldPosition)?.also { oldItem ->
                    val header = getHeaderOf(oldItem)
                    if (header != null) {
                        val oldHeader = getSectionHeader(oldPosition)
                        if (oldHeader != null && oldHeader != header) {
                            linkHeaderTo(oldItem, oldHeader, Payload.LINK)
                        }
                        linkHeaderTo(getItem(newPosition), header, Payload.LINK)
                    }
                }

            }
        }
    }

    /*------------------------------------*/
    /* IMPLEMENT ADAPTER CALLBACK METHODS */
    /*-----------------------------------*/

    override fun onActionStateChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
        if (onItemMoveListener != null)
            onItemMoveListener?.onActionStateChanged(this, viewHolder, actionState)
        else if (onItemSwipeListener != null) {
            onItemSwipeListener?.onActionStateChanged(this, viewHolder, actionState)
        }
    }

    override fun shouldMove(fromPosition: Int, toPosition: Int): Boolean {
        val toItem = getItem(toPosition)
        return !(mScrollableHeaders.contains(toItem) || mScrollableFooters.contains(toItem)) && (onItemMoveListener == null || true == onItemMoveListener?.shouldMoveItem(
            this,
            fromPosition,
            toPosition
        ))
    }


    override fun onItemMove(fromPosition: Int, toPosition: Int): Boolean {
        swapItems(listData, fromPosition, toPosition)
        // After the swap, delegate further actions to the user
        onItemMoveListener?.onItemMove(this, fromPosition, toPosition)
        return true
    }

    override fun onItemSwiped(position: Int, direction: Int) {
        onItemSwipeListener?.onItemSwipe(this, position, direction)
    }

    /*------------------------*/
    /* OTHERS PRIVATE METHODS */
    /*------------------------*/

    /**
     * Internal mapper to remember and add all view types for the items.
     *
     * @param item the item to map
     */
    private fun mapViewTypeFrom(item: T) {
        if (!mTypeInstances.containsKey(item.getItemViewType())) {
            mTypeInstances[item.getItemViewType()] = item
            Log.d(
                "Mapped viewType ${item.getItemViewType()} from ${item::class.java.simpleName}",
                TAG
            )
        }
    }

    /**
     * Retrieves the type instance remembered within the FlexibleAdapter for an item.
     *
     * @param viewType the view type of the item (layout resourceId)
     * @return the IFlexible instance, creator of the view type
     */
    private fun getViewTypeInstance(viewType: Int): T? {
        return mTypeInstances[viewType]
    }

    /**
     * @param item the item to compare
     * @return the removed item if found, null otherwise
     */
    private fun getPendingRemovedItem(item: T): RestoreInfo? {
        for (restoreInfo in mRestoreList) {
            // refPosition >= 0 means that position has been calculated and restore is ongoing
            if (restoreInfo.item.equals(item) && restoreInfo.refPosition < 0) return restoreInfo
        }
        return null
    }


    /**
     * @param expandable the expandable, parent of this sub item
     * @param item       the deleted item
     */
    private fun createRestoreSubItemInfo(expandable: IExpandable<*, *>, item: T) {
        val siblings = getExpandableList(expandable, false)
        val childPosition = siblings.indexOf(item)
        mRestoreList.add(RestoreInfo(expandable as T, item, childPosition))
        Log.d(
            "Recycled SubItem ${mRestoreList[mRestoreList.size - 1]} with Parent position=${
                getGlobalPositionOf(
                    expandable
                )
            }", TAG
        )
    }


    /**
     * @param position the position of the item to retain.
     * @param item     the deleted item
     */
    private fun createRestoreItemInfo(position: Int, item: T) {
        // Collapse Parent before removal if it is expanded!
        if (isExpanded(item)) collapse(position)
        // Get the reference of the previous item (getItem returns null if outOfBounds)
        // If null, it will be restored at position = 0
        var refItem = getItem(position - 1)
        if (refItem != null) {
            // Check if the refItem is a child of an Expanded parent, take the parent!
            val expandable = getExpandableOf(refItem)
            if (expandable != null) refItem = expandable as T?
            mRestoreList.add(RestoreInfo(refItem as T, item, -1))
            Log.d("Recycled Item ${mRestoreList[mRestoreList.size - 1]} on position=$position", TAG)
        }
    }

    /**
     * @param expandable the parent item
     * @return the list of the subItems not hidden
     */
    private fun getExpandableList(expandable: IExpandable<*, *>, isRecursive: Boolean): List<T> {
        val subItems = mutableListOf<T>()
        if (hasSubItems(expandable)) {
            val allSubItems = expandable.getSubItems()
            for (subItem in allSubItems) {
                // Pick up only no hidden items (doesn't get into account the filtered items)
                if (!(subItem as IFlexible<out FlexibleExpandableViewHolder>).isHidden()) {
                    // Add the current subitem
                    subItems.add(subItem as T)
                    // If expandable, expanded, and of non-zero size, recursively add sub-subItems
                    if (isRecursive && isExpanded(subItem) &&
                        (subItem as IExpandable<*, *>).getSubItems().isNotEmpty()
                    ) {
                        subItems.addAll(getExpandableList(subItem as IExpandable<*, *>, true))
                    }
                }
            }
        }
        return subItems
    }

    /**
     * Allows or disallows the request to collapse the Expandable item.
     *
     * @param startPosition helps to improve performance, so we can avoid a new search for position
     * @param subItems      the list of sub items to check
     * @return true if at least 1 subItem is currently selected, false if no subItems are selected
     * search is non-recursive
     */
    private fun hasSubItemsSelected(startPosition: Int, subItems: List<T>): Boolean {
        var position = startPosition
        for (subItem in subItems) {
            if (isSelected(++position) || isExpanded(subItem) && hasSubItemsSelected(
                    position,
                    getExpandableList(subItem as IExpandable<*, *>, false)
                )
            )
                return true
        }
        return false
    }

    /**
     * Performs *safe* smooth scroll with a delay of {@value #AUTO_SCROLL_DELAY} ms.
     *
     * @param position the position to scroll to.
     */
    fun smoothScrollToPosition(position: Int) {
        // Must be delayed to give time at RecyclerView to recalculate positions after a layout change
        coroutineScope.launch(Dispatchers.Main) {
            delay(AUTO_SCROLL_DELAY)
            performScroll(position)
        }
    }

    private fun performScroll(position: Int) {
        mRecyclerView.smoothScrollToPosition(Math.min(Math.max(0, position), itemCount - 1))
    }

    private fun autoScrollWithDelay(position: Int, subItemsCount: Int) {
        // Must be delayed to give time at RecyclerView to recalculate positions after a layout change
        coroutineScope.launch(Dispatchers.Main) {
            delay(AUTO_SCROLL_DELAY)
            // #492 - NullPointerException when expanding item with auto-scroll
            val firstVisibleItem =
                getFlexibleLayoutManager().findFirstCompletelyVisibleItemPosition()
            val lastVisibleItem = getFlexibleLayoutManager().findLastCompletelyVisibleItemPosition()
            val itemsToShow = position + subItemsCount - lastVisibleItem
            // log.v("autoScroll itemsToShow=%s firstVisibleItem=%s lastVisibleItem=%s RvChildCount=%s", itemsToShow, firstVisibleItem, lastVisibleItem, mRecyclerView.getChildCount());
            if (itemsToShow > 0) {
                val scrollMax = position - firstVisibleItem
                val scrollMin = Math.max(0, position + subItemsCount - lastVisibleItem)
                var scrollBy = Math.min(scrollMax, scrollMin)
                val spanCount = getFlexibleLayoutManager().getSpanCount()
                if (spanCount > 1) {
                    scrollBy = scrollBy % spanCount + spanCount
                }
                val scrollTo = firstVisibleItem + scrollBy
                Log.d(
                    "autoScroll scrollMin=$scrollMin scrollMax=$scrollMax scrollBy=$scrollBy scrollTo=$scrollTo",
                    TAG
                )
                performScroll(scrollTo)
            } else if (position < firstVisibleItem) {
                performScroll(position)
            }
        }
    }

    private fun adjustSelected(startPosition: Int, itemCount: Int) {
        val selectedPositions = getSelectedPositions()
        var adjusted = false
        var diff = ""
        if (itemCount > 0) {
            // Reverse sorting is necessary because using Set removes duplicates
            // during adjusting, so we scan backward.
            selectedPositions.sortedByDescending { it }
            diff = "+"
        }
        for (position in selectedPositions) {
            if (position >= startPosition) {
                removeSelection(position)
                addAdjustedSelection(Math.max(position + itemCount, startPosition))
                adjusted = true
            }
        }
        if (adjusted) Log.d("AdjustedSelected(${diff + itemCount})=${getSelectedPositions()}", TAG)
    }

    /**
     * Helper method to invalidate the item decorations after the provided delay using Coroutines.
     *
     * The delay will give time to the LayoutManagers to complete the layout of the child views.
     * **Tip:** A delay of `100ms` should be enough, anyway it can be customized.
     *
     * @param delay delay to invalidate the decorations
     */
    fun invalidateItemDecorations(@IntRange(from = 0) delay: Long) {
        coroutineScope.launch(Dispatchers.Main) {
            delay(delay)
            mRecyclerView?.invalidateItemDecorations()
        }
    }


    /*----------------*/
    /* INSTANCE STATE */
    /*----------------*/

    /**
     * Save the state of the current expanded items.
     *
     * @param outState Current state
     */
    override fun onSaveInstanceState(outState: Bundle) {
        // Save selection state
        if (mScrollableHeaders.size > 0) {
            // We need to rollback the added item positions if headers were added lately
            adjustSelected(0, -mScrollableHeaders.size)
        }
        super.onSaveInstanceState(outState)
        // Save selection coherence
        outState.putBoolean(EXTRA_CHILD, this.childSelected)
        outState.putBoolean(EXTRA_PARENT, this.parentSelected)
        outState.putInt(EXTRA_LEVEL, this.mSelectedLevel)
        // Current filter. Old text is not saved otherwise animateTo() cannot be called
        outState.putString(EXTRA_FILTER, getFilter())
        // Save headers shown status
        outState.putBoolean(EXTRA_HEADERS, this.headersShown)
        outState.putBoolean(EXTRA_STICKY, areHeadersSticky())
    }

    /**
     * Restore the previous state of the expanded items.
     *
     * @param savedInstanceState Previous state
     */
    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        // Restore headers shown status
        val headersShown = savedInstanceState.getBoolean(EXTRA_HEADERS)
        if (!headersShown) {
            hideAllHeaders()
        } else if (!this.headersShown) {
            showAllHeadersWithReset(true)
        }
        this.headersShown = headersShown
        if (savedInstanceState.getBoolean(EXTRA_STICKY) && !areHeadersSticky()) {
            setStickyHeaders(true)
        }
        // Restore selection state
        super.onRestoreInstanceState(savedInstanceState)
        if (mScrollableHeaders.size > 0) {
            // We need to restore the added item positions if headers were added early
            adjustSelected(0, mScrollableHeaders.size)
        }
        // Restore selection coherence
        this.parentSelected = savedInstanceState.getBoolean(EXTRA_PARENT)
        this.childSelected = savedInstanceState.getBoolean(EXTRA_CHILD)
        this.mSelectedLevel = savedInstanceState.getInt(EXTRA_LEVEL)
        // Current filter (old filter must not be saved)
        this.mFilterEntity = StringBuilder(savedInstanceState.getString(EXTRA_FILTER, ""))
    }


    private fun filterAsync(action: Int, newItems: List<T>?): Job {
        return coroutineScope.launch(Dispatchers.IO) {
            Log.d("filterAsync: $action, ${newItems?.size}")
            if (endlessLoading) {
                Log.d("Cannot filter while endlessLoading", TAG)
                cancel("Cannot filter while endlessLoading")
                return@launch
            }

            val listDoing = newItems?.toMutableList() ?: mutableListOf()

            if (isRestoreInTime()) {
                Log.d("Removing all deleted items before filtering/updating", TAG)
                listDoing.removeAll(getDeletedItems())
                withContext(Dispatchers.Main) {
                    onDeleteCompleteListener?.onDeleteConfirmed(DISMISS_EVENT_MANUAL) //  = 3
                }
            }

            startTimeFilter = System.currentTimeMillis()
            when (action) {
                ACTION_UPDATE -> {
                    Log.d("filterAsync - started ACTION_UPDATE", TAG)
                    prepareItemsForUpdate(listDoing)
                    toggleAnimate(listDoing, Payload.CHANGE)
                    Log.d("filterAsync - ended ACTION_UPDATE", TAG)
                }

                ACTION_FILTER -> {
                    Log.d("filterAsync - started ACTION_FILTER", TAG)
                    filterItemsAsync(listDoing)
                    Log.d("filterAsync - ended ACTION_FILTER", TAG)
                }
            }

            withContext(Dispatchers.Main) {
                if (diffResult != null || mNotifications != null) {
                    // Execute results
                    when (action) {
                        ACTION_UPDATE -> {
                            // Notify all the changes
                            executeNotifications(Payload.CHANGE)
                            onPostUpdate()
                        }

                        ACTION_FILTER -> {
                            // Notify all the changes
                            executeNotifications(Payload.FILTER)
                            onPostFilter()
                        }
                    }
                }
                mFilterJob = null
            }
        }
    }

    private fun prepareItemsForUpdate(newItems: MutableList<T>) {
        // Clear cache of bound view holders
        if (notifyChangeOfUnfilteredItems) {
            discardBoundViewHolders()
        }
        // Display Scrollable Headers and Footers
        restoreScrollableHeadersAndFooters(newItems)

        var position = 0
        var sameHeader: IHeader<*>? = null
        // We use 1 cycle for expanding And display headers
        // to optimize the operations of adding hidden items/subItems
        while (position < newItems.size) {
            val item = newItems[position]
            // Expand Expandable
            if (isExpanded(item)) {
                val expandable = item as IExpandable<*, *>
                expandable.setExpanded(true)
                val subItems = getExpandableList(expandable, false)
                val itemCount = newItems.size
                if (position < itemCount) {
                    newItems.addAll(position + 1, subItems)
                } else {
                    newItems.addAll(subItems)
                }
            }
            // Display headers too
            if (!headersShown && isHeader(item) && !item.isHidden()) {
                headersShown = true
            }
            getHeaderOf(item)?.also {
                if (it != sameHeader && !isExpandableItem(it as T)) {
                    it.setHidden(false)
                    sameHeader = it
                    newItems.add(position, it as T)
                    position++
                }
            }
            position++
        }
    }

    /**
     * This method is called only in case of granular notifications (not when notifyDataSetChanged) and after the
     * execution of Update, it calls the implementation of the [OnUpdateListener] for the emptyView.
     *
     * @see updateDataSet
     */
    @CallSuper
    fun onPostUpdate() {
        // Call listener to update EmptyView, assuming the update always made a change
        onUpdateListener?.onUpdateEmptyView(this, getMainItemCount())
    }

    /**
     * This method is called after the execution of Filter, it calls the
     * implementation of the [OnFilterListener] for the filterView.
     *
     * @see filterItems
     */
    @CallSuper
    fun onPostFilter() {
        // Call listener to update FilterView, assuming the filter always made a change
        onFilterListener?.onUpdateFilterView(this, getMainItemCount())
    }


    private inner class RestoreInfo(val refItem: T, val item: T, var relativePosition: Int) {
        var refPosition = -1

        /**
         * @return the position where the deleted item should be restored
         */
        fun getRestorePosition(isChild: Boolean): Int {
            if (refPosition < 0) {
                refPosition = getGlobalPositionOf(refItem)
            }
            val item = getItem(refPosition)
            if (isChild && isExpandableItem(item)) {
                // Assert the expandable children are collapsed
                recursiveCollapse(refPosition, getCurrentChildren(item as IExpandable<*, *>), 0)
            } else if (isExpanded(item) && !isChild) {
                refPosition += getExpandableList(item as IExpandable<*, *>, true).size + 1
            } else {
                refPosition++
            }
            return refPosition
        }

        override fun toString(): String {
            return "RestoreInfo[item=$item, refItem=$refItem]"
        }
    }


    private inner class AdapterDataObserver : RecyclerView.AdapterDataObserver() {

        private fun adjustPositions(positionStart: Int, itemCount: Int) {
            if (adjustSelected)
            // Don't, if remove range / restore
                adjustSelected(positionStart, itemCount)
            adjustSelected = true
        }

        private fun updateStickyHeader(positionStart: Int) {
            val stickyPosition = getStickyPosition()
            // #499 - Bulk operation properly updates the same sticky header once, while each
            // independent operation (multiple events) updates the sticky header multiple times.
            if (stickyPosition >= 0 && stickyPosition == positionStart) {
                Log.d("updateStickyHeader position=$stickyPosition")
                // #320 - To include adapter changes just notified we need a new layout pass:
                // We must give time to LayoutManager otherwise the findFirstVisibleItemPosition()
                // will return wrong position!
                coroutineScope.launch(Dispatchers.Main) {
                    delay(100L)
                    if (areHeadersSticky()) mStickyHeaderHelper?.updateOrClearHeader(
                        true
                    )
                }
            }
        }

        /* Triggered by notifyDataSetChanged() */
        override fun onChanged() {
            updateStickyHeader(getStickyPosition())
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            adjustPositions(positionStart, itemCount)
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            updateStickyHeader(positionStart)
            adjustPositions(positionStart, -itemCount)
        }

        override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
            updateStickyHeader(positionStart)
        }
    }

}