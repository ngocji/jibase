package com.jibase.iflexible.adapter

import androidx.lifecycle.Lifecycle
import androidx.paging.AsyncPagingDataDiffer
import androidx.paging.CombinedLoadStates
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListUpdateCallback
import androidx.recyclerview.widget.RecyclerView
import com.jibase.iflexible.items.interfaceItems.IFlexible
import com.jibase.utils.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * A [FlexibleAdapter] variant with full Paging 3 support.
 *
 * ## How it works
 *
 * [AsyncPagingDataDiffer] owns the ordering and diffing of paging items.
 * [listData] is the source of truth for the full adapter list and is rebuilt from
 * scrollable headers + paging snapshot + scrollable footers **before** every `notifyItem*`
 * call, so all FlexibleAdapter internals always see a consistent state.
 *
 * ```
 * submitData(PagingData<T>)
 *   └─ AsyncPagingDataDiffer          ← owns paging item ordering & diffing
 *         └─ ListUpdateCallback
 *               └─ syncListData()     ← headers + snapshot + footers → listData
 *                     └─ notifyItem*  ← positions offset by headers.size
 * ```
 *
 * ## Default DiffUtil.ItemCallback
 *
 * When no [diffCallback] is provided, [DefaultItemCallback] is used:
 * - `areItemsTheSame` → compares [IFlexible.getIdView] (same logic as [FlexibleDiffCallback])
 * - `areContentsTheSame` → delegates to [equals]
 *
 * Override [IFlexible.getIdView] and [equals] in your item class to control diffing behaviour,
 * or supply a custom [DiffUtil.ItemCallback] for full control.
 *
 * ## Recommended PagingConfig
 *
 * ```kotlin
 * PagingConfig(pageSize = 20, enablePlaceholders = false)
 * ```
 * With `enablePlaceholders = false` every item in the snapshot is non-null, which means
 * [listData] is always a clean `List<T>` and FlexibleAdapter features work without
 * special-casing null placeholders.
 *
 * ## Features preserved from FlexibleAdapter
 * - Expandable items (state lives in item objects, not positions)
 * - Sticky / Scrollable headers and footers
 * - Selection and ActionMode
 * - Scroll animation
 * - Click / LongClick listeners
 * - Drag & Drop and Swipe (UI only; see conflict note below)
 * - Pending Init — configs set before RecyclerView attaches are still applied on attach
 *
 * ## Features that conflict with server-driven paging (do NOT use)
 * - **Client-side filter** ([filterItems]): [syncListData] overwrites [listData] on every
 *   Paging 3 load, resetting any active filter. Use server-side filtering (query param in
 *   your [androidx.paging.PagingSource]) instead.
 * - **[setEndlessScrollListener] / [onLoadMoreComplete]**: replaced by Paging 3's own
 *   loading pipeline; both are no-ops in this class.
 * - **Drag & Drop reordering**: local position changes are overwritten on the next
 *   [refresh] / invalidate cycle.
 * - **Undo / Delete** without a [androidx.paging.RemoteMediator]: items deleted locally
 *   reappear after the next refresh because the server still holds them.
 */
@Suppress("UNCHECKED_CAST")
open class FlexiblePagingAdapter<T : IFlexible<*>>(
    diffCallback: DiffUtil.ItemCallback<T> = DefaultItemCallback(),
    hasStateId: Boolean = false
) : FlexibleAdapter<T>(mutableListOf(), hasStateId) {

    /**
     * Default [DiffUtil.ItemCallback] that mirrors the logic of [FlexibleDiffCallback]:
     * - Identity check uses [IFlexible.getIdView] (defaults to the item's hash code as a string).
     * - Content check delegates to [equals], consistent with [FlexibleAdapter]'s filter diffing.
     *
     * Override [IFlexible.getIdView] and [equals] in your item class to customise this behaviour
     * without needing to supply a custom callback.
     */
    class DefaultItemCallback<T : IFlexible<*>> : DiffUtil.ItemCallback<T>() {
        override fun areItemsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem.getIdView() == newItem.getIdView()
        }

        override fun areContentsTheSame(oldItem: T, newItem: T): Boolean {
            return oldItem == newItem
        }
    }

    private companion object {
        const val TAG = "FlexiblePagingAdapter"
    }

    /* ────────────────────────────────────────────────────── */
    /*  PAGING CORE                                           */
    /* ────────────────────────────────────────────────────── */

    /**
     * The differ that drives item diffing and list updates.
     *
     * A custom [ListUpdateCallback] is used instead of the default [AdapterListUpdateCallback]
     * so that [syncListData] runs **before** each `notifyItem*` call. This guarantees that
     * [listData] is up-to-date at the moment RecyclerView calls [onBindViewHolder].
     */
    private val differ = AsyncPagingDataDiffer(
        diffCallback = diffCallback,
        updateCallback = object : ListUpdateCallback {
            override fun onInserted(position: Int, count: Int) {
                syncListData()
                notifyItemRangeInserted(position + getScrollableHeaders().size, count)
            }

            override fun onRemoved(position: Int, count: Int) {
                syncListData()
                notifyItemRangeRemoved(position + getScrollableHeaders().size, count)
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                syncListData()
                notifyItemMoved(
                    fromPosition + getScrollableHeaders().size,
                    toPosition + getScrollableHeaders().size
                )
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                syncListData()
                notifyItemRangeChanged(position + getScrollableHeaders().size, count, payload)
            }
        }
    )

    /**
     * Rebuilds [listData] from scrollable headers + paging snapshot + scrollable footers.
     *
     * Must be called **before** any `notifyItem*` so that [getItem] and all FlexibleAdapter
     * internals see a consistent state when RecyclerView calls [onBindViewHolder].
     * Headers/footers are re-inserted from their respective lists, so they survive every
     * paging refresh cycle.
     */
    private fun syncListData() {
        val headers = getScrollableHeaders()
        val footers = getScrollableFooters()
        val pagingItems = differ.snapshot().items
        val progress = if (isProgressVisible) listOfNotNull(mPagingProgressItem) else emptyList()
        listData = (headers + pagingItems + footers + progress).toMutableList()
    }

    /* ────────────────────────────────────────────────────── */
    /*  PAGING 3 PUBLIC API                                   */
    /* ────────────────────────────────────────────────────── */

    /**
     * Submits a new [PagingData] to be diffed and displayed.
     *
     * This is a **suspending** call that returns only after the differ has fully processed
     * the data. Collect your paging [kotlinx.coroutines.flow.Flow] with
     * [kotlinx.coroutines.flow.collectLatest] so that an in-flight call is cancelled when
     * a newer [PagingData] arrives:
     *
     * ```kotlin
     * lifecycleScope.launch {
     *     viewModel.pagingFlow.collectLatest { adapter.submitData(it) }
     * }
     * ```
     */
    suspend fun submitData(pagingData: PagingData<T>) {
        differ.submitData(pagingData)
    }

    /**
     * Submits a new [PagingData] tied to the given [lifecycle].
     *
     * The submission is automatically cancelled when [lifecycle] moves to the DESTROYED state,
     * making this the preferred overload when a coroutine scope is not readily available:
     *
     * ```kotlin
     * viewModel.pagingFlow.observe(viewLifecycleOwner) { adapter.submitData(lifecycle, it) }
     * ```
     */
    fun submitData(lifecycle: Lifecycle, pagingData: PagingData<T>) {
        differ.submitData(lifecycle, pagingData)
    }

    /**
     * A [Flow] of [CombinedLoadStates] that reflects the current loading state for each
     * load type (refresh, prepend, append).
     *
     * Typical usage:
     * ```kotlin
     * adapter.addLoadStateListener { states ->
     *     swipeRefresh.isRefreshing = states.refresh is LoadState.Loading
     *     errorView.isVisible = states.refresh is LoadState.Error
     * }
     * ```
     */
    val loadStateFlow: Flow<CombinedLoadStates> = differ.loadStateFlow

    /**
     * Registers a [listener] that is called whenever [loadStateFlow] emits a new value.
     *
     * @see removeLoadStateListener
     */
    fun addLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        differ.addLoadStateListener(listener)
    }

    /**
     * Removes a previously registered [listener].
     *
     * @see addLoadStateListener
     */
    fun removeLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        differ.removeLoadStateListener(listener)
    }

    /**
     * Retries the last failed load (either a refresh or an append).
     *
     * Has no effect if the last load succeeded or if no load has been attempted yet.
     */
    fun retry() = differ.retry()

    /**
     * Invalidates the current [PagingData] and triggers a fresh load from page 1.
     *
     * Use this for pull-to-refresh or when the underlying data source has changed and the
     * displayed list needs to be discarded entirely:
     *
     * ```kotlin
     * swipeRefreshLayout.setOnRefreshListener { adapter.refresh() }
     * ```
     */
    fun refresh() = differ.refresh()

    /* ────────────────────────────────────────────────────── */
    /*  PROGRESS ITEM                                         */
    /* ────────────────────────────────────────────────────── */

    /** Shown at the bottom while the next page is being fetched (append [LoadState.Loading]). */
    private var mPagingProgressItem: T? = null

    /**
     * Whether [mPagingProgressItem] is currently appended to [listData].
     * Tracked separately so [syncListData] can include it without touching [mScrollableFooters].
     */
    private var isProgressVisible = false

    /**
     * Coroutine scope dedicated to observing [loadStateFlow].
     * Cancelled in [onDetachedFromRecyclerView] to avoid leaking the observer.
     */
    private val pagingScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Active collection job; replaced on each [startObservingLoadState] call. */
    private var loadStateJob: Job? = null

    /**
     * Sets the item shown while the next page is loading (append [LoadState.Loading]).
     * Hidden as soon as loading finishes or errors.
     *
     * The item is appended directly to [listData] — it does **not** go through
     * [mScrollableFooters], so [getScrollableFooters] will never return it and
     * [syncListData] handles it independently of user-added scrollable footers.
     *
     * For "no more data" UI, add a scrollable footer manually via [addScrollableFooter]
     * inside an [addLoadStateListener] when [LoadState.NotLoading.endOfPaginationReached] is true.
     *
     * Safe to call before or after the adapter attaches to a RecyclerView.
     *
     * @param progressItem the loading indicator item, or `null` to disable
     * @return this adapter for chaining
     */
    override fun setEndlessProgressItem(progressItem: T?): FlexiblePagingAdapter<T> {
        if (isProgressVisible) {
            val oldPos = listData.indexOf(mPagingProgressItem)
            isProgressVisible = false
            mPagingProgressItem = progressItem
            syncListData()
            if (oldPos >= 0) notifyItemRemoved(oldPos)
        } else {
            mPagingProgressItem = progressItem
        }
        if (progressItem != null && recyclerViewHasInitialized()) startObservingLoadState()
        else if (progressItem == null) {
            loadStateJob?.cancel()
            loadStateJob = null
        }
        return this
    }

    /**
     * Starts (or restarts) the [loadStateFlow] collection.
     *
     * Shows [mPagingProgressItem] by appending it to [listData] when append is
     * [LoadState.Loading]; removes it directly from [listData] otherwise.
     * Neither operation touches [mScrollableFooters].
     */
    private fun startObservingLoadState() {
        loadStateJob?.cancel()
        loadStateJob = pagingScope.launch {
            loadStateFlow.collectLatest { loadState ->
                Log.d("Change state — $loadState, progressItem=$mPagingProgressItem", TAG)
                mPagingProgressItem ?: return@collectLatest
                val shouldShow = loadState.append is LoadState.Loading
                if (shouldShow == isProgressVisible) return@collectLatest
                if (shouldShow) {
                    isProgressVisible = true
                    syncListData()
                    notifyItemInserted(listData.size - 1)
                    Log.d("Append loading — showing progress item", TAG)
                } else {
                    val oldPos = listData.indexOf(mPagingProgressItem)
                    isProgressVisible = false
                    syncListData()
                    if (oldPos >= 0) notifyItemRemoved(oldPos)
                    Log.d("Append done — hiding progress item", TAG)
                }
            }
        }
    }

    /* ────────────────────────────────────────────────────── */
    /*  LIFECYCLE                                             */
    /* ────────────────────────────────────────────────────── */

    /**
     * Starts progress-item observation (if a progress item has been set) once the
     * RecyclerView is ready, then delegates to [FlexibleAdapter] for pending-config application.
     */
    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        if (mPagingProgressItem != null) startObservingLoadState()
    }

    /**
     * Cancels the [loadStateFlow] observer and the [pagingScope] to prevent coroutine leaks,
     * then delegates to [FlexibleAdapter] for its own cleanup.
     */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        loadStateJob?.cancel()
        loadStateJob = null
        pagingScope.cancel()
        super.onDetachedFromRecyclerView(recyclerView)
    }

    /* ────────────────────────────────────────────────────── */
    /*  DISABLED ENDLESS SCROLL                               */
    /* ────────────────────────────────────────────────────── */

    /**
     * No-op. Paging 3 owns the loading lifecycle and triggers the next page automatically
     * based on [androidx.paging.PagingConfig.prefetchDistance]; this method must not interfere.
     */
    override fun onLoadMore(position: Int) = Unit

    /**
     * No-op. Data insertion after a page load is handled entirely by [AsyncPagingDataDiffer]
     * via [syncListData]; calling this method directly would corrupt the differ's state.
     */
    override fun onLoadMoreComplete(newItems: List<T>, delay: Long) = Unit
}
