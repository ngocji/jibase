package com.jibase.iflexible.adapter

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.coroutineScope
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
                Log.d("differ.onInserted — position=$position, count=$count", TAG)
                syncListData()
                val offsetPosition = position + getScrollableHeaders().size
                notifyItemRangeInserted(offsetPosition, count)
                externalListUpdateCallbacks.forEach { it.onInserted(offsetPosition, count) }
            }

            override fun onRemoved(position: Int, count: Int) {
                Log.d("differ.onRemoved — position=$position, count=$count", TAG)
                syncListData()
                val offsetPosition = position + getScrollableHeaders().size
                notifyItemRangeRemoved(offsetPosition, count)
                externalListUpdateCallbacks.forEach { it.onRemoved(offsetPosition, count) }
            }

            override fun onMoved(fromPosition: Int, toPosition: Int) {
                Log.d("differ.onMoved — fromPosition=$fromPosition, toPosition=$toPosition", TAG)
                syncListData()
                val headersSize = getScrollableHeaders().size
                val offsetFrom = fromPosition + headersSize
                val offsetTo = toPosition + headersSize
                notifyItemMoved(offsetFrom, offsetTo)
                externalListUpdateCallbacks.forEach { it.onMoved(offsetFrom, offsetTo) }
            }

            override fun onChanged(position: Int, count: Int, payload: Any?) {
                Log.d("differ.onChanged — position=$position, count=$count, payload=$payload", TAG)
                syncListData()
                val offsetPosition = position + getScrollableHeaders().size
                notifyItemRangeChanged(offsetPosition, count, payload)
                externalListUpdateCallbacks.forEach { it.onChanged(offsetPosition, count, payload) }
            }
        }
    )

    /** External [ListUpdateCallback]s notified after each `notifyItem*` call above dispatches. */
    private val externalListUpdateCallbacks = mutableListOf<ListUpdateCallback>()

    /**
     * Registers [callback] to be notified after every [differ] update has been applied to
     * [listData] and the corresponding `notifyItem*` call has been dispatched to the
     * RecyclerView.
     *
     * Positions passed to [callback] are already offset by [getScrollableHeaders]'s size,
     * i.e. they match the actual adapter position used in the `notifyItem*` call — not the
     * raw paging-snapshot position reported by [AsyncPagingDataDiffer].
     *
     * @see removeListUpdateCallback
     */
    fun addListUpdateCallback(callback: ListUpdateCallback) {
        Log.d("addListUpdateCallback — callback=$callback", TAG)
        if (!externalListUpdateCallbacks.contains(callback)) {
            externalListUpdateCallbacks.add(callback)
        }
    }

    /**
     * Unregisters a [callback] previously added via [addListUpdateCallback].
     */
    fun removeListUpdateCallback(callback: ListUpdateCallback) {
        Log.d("removeListUpdateCallback — callback=$callback", TAG)
        externalListUpdateCallbacks.remove(callback)
    }

    /**
     * Rebuilds [listData] from scrollable headers + paging snapshot + scrollable footers.
     *
     * Must be called **before** any `notifyItem*` so that [getItem] and all FlexibleAdapter
     * internals see a consistent state when RecyclerView calls [onBindViewHolder].
     * Headers/footers are re-inserted from their respective lists, so they survive every
     * paging refresh cycle.
     *
     * Called from three places:
     * 1. Inside the [differ]'s [ListUpdateCallback] — runs before each `notifyItem*`.
     * 2. Directly after [clearData] settles — reliable there because [PagingData.empty] is
     *    static/finite, so [AsyncPagingDataDiffer.submitData] returns promptly. The same call in
     *    [submitData] is *not* reliable for a real, live [PagingData]: per
     *    [AsyncPagingDataDiffer.submitData]'s contract, that suspend call only returns once the
     *    generation is superseded/invalidated, not once the current page settles.
     * 3. From [startObservingLoadState] when append reports
     *    [LoadState.NotLoading.endOfPaginationReached] — the reliable fix for a real,
     *    live [PagingData] that diffs to zero changes (e.g. two consecutive empty pages),
     *    since (1) never fires in that case and (2) can't be relied on either. Only active while
     *    a progress item is configured (see [setEndlessProgressItem]).
     */
    private fun syncListData() {
        val headers = getScrollableHeaders()
        val footers = getScrollableFooters()
        val pagingItems = differ.snapshot().items
        val progress = if (isProgressVisible) listOfNotNull(mPagingProgressItem) else emptyList()
        listData = (headers + pagingItems + footers + progress).toMutableList()
        Log.d(
            "syncListData — headers=${headers.size}, pagingItems=${pagingItems.size}, " +
                    "footers=${footers.size}, progress=${progress.size}, total=${listData.size}",
            TAG
        )
        onPostUpdate()
    }

    /**
     * Returns the item at [position] and, for positions that map to paging items, also calls
     * [AsyncPagingDataDiffer.getItem] so Paging 3 is notified of the access and can trigger
     * prefetching of the next page.
     *
     * Without this call, scrolling to the bottom would never trigger load-more because the
     * differ only schedules a fetch when it sees an item access near the boundary.
     */
    override fun getItem(position: Int): T? {
        val headersSize = getScrollableHeaders().size
        val pagingItemCount = differ.itemCount
        val pagingIndex = position - headersSize
        if (pagingIndex in 0 until pagingItemCount) {
            differ.getItem(pagingIndex)
        }
        val item = listData.getOrNull(position)
        Log.d(
            "getItem — position=$position, pagingIndex=$pagingIndex, pagingItemCount=$pagingItemCount, item=$item",
            TAG
        )
        return item
    }

    /* ────────────────────────────────────────────────────── */
    /*  PAGING 3 PUBLIC API                                   */
    /* ────────────────────────────────────────────────────── */

    private var _isPagingDataSubmitted = false

    /** `true` once [submitData] has been called at least once. */
    val isPagingDataSubmitted: Boolean get() = _isPagingDataSubmitted

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
     *
     * [syncListData] is force-called once more after the differ settles, even if it reports
     * no changes (e.g. submitting two consecutive empty pages) — see [syncListData] for why
     * that matters.
     */
    suspend fun submitData(pagingData: PagingData<T>) {
        Log.d("submitData — pagingData=$pagingData", TAG)
        _isPagingDataSubmitted = true
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
     *
     * Implemented on top of the suspending [submitData] (rather than delegating to
     * [AsyncPagingDataDiffer.submitData]'s own lifecycle overload) so the post-settle
     * [syncListData] call still runs — see [submitData] and [syncListData].
     */
    fun submitData(lifecycle: Lifecycle, pagingData: PagingData<T>) {
        Log.d("submitData(lifecycle) — pagingData=$pagingData, lifecycle=$lifecycle", TAG)
        _isPagingDataSubmitted = true
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
        Log.d("addLoadStateListener — listener=$listener", TAG)
        differ.addLoadStateListener(listener)
    }

    /**
     * Removes a previously registered [listener].
     *
     * @see addLoadStateListener
     */
    fun removeLoadStateListener(listener: (CombinedLoadStates) -> Unit) {
        Log.d("removeLoadStateListener — listener=$listener", TAG)
        differ.removeLoadStateListener(listener)
    }

    /**
     * Retries the last failed load (either a refresh or an append).
     *
     * Has no effect if the last load succeeded or if no load has been attempted yet.
     */
    fun retry() {
        Log.d("retry", TAG)
        differ.retry()
    }

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
    fun refresh() {
        Log.d("refresh", TAG)
        differ.refresh()
    }

    /**
     * Clears all paging data, resetting the adapter back to its pristine, pre-[submitData]
     * state: [listData] collapses to just scrollable headers/footers and [isPagingDataSubmitted]
     * becomes `false` again.
     *
     * This is a **suspending** call that returns only after the differ has fully processed
     * the clear, same as [submitData]:
     *
     * ```kotlin
     * lifecycleScope.launch { adapter.clearData() }
     * ```
     *
     * [syncListData] is force-called once more after the differ settles, even if it reports
     * no changes (e.g. the differ was already empty) — see [syncListData] for why that matters.
     */
    suspend fun clearData() {
        Log.d("clearData — before, itemCount=$itemCount", TAG)
        _isPagingDataSubmitted = false
        differ.submitData(PagingData.empty())
        Log.d("clearData — done, itemCount=$itemCount", TAG)
    }

    /**
     * Clears all paging data, tied to the given [lifecycle]. See [clearData] and the
     * [submitData] lifecycle overload for details.
     */
    fun clearData(lifecycle: Lifecycle) {
        Log.d("clearData(lifecycle) — lifecycle=$lifecycle", TAG)
        _isPagingDataSubmitted = false
        lifecycle.coroutineScope.launch { clearData() }
    }

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
        Log.d(
            "setEndlessProgressItem — progressItem=$progressItem, isProgressVisible=$isProgressVisible",
            TAG
        )
        if (isProgressVisible) {
            val oldPos = listData.indexOf(mPagingProgressItem)
            isProgressVisible = false
            mPagingProgressItem = progressItem
            syncListData()
            if (oldPos >= 0) notifyItemRemoved(oldPos)
        } else {
            mPagingProgressItem = progressItem
        }
        if (progressItem != null && recyclerViewHasInitialized()) {
            startObservingLoadState()
        } else if (progressItem == null) {
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
     *
     * Also force-syncs [listData] whenever append reports
     * [LoadState.NotLoading.endOfPaginationReached] — unlike a zero-arg [PagingData.empty] (whose
     * `sourceLoadStates` is `null` and therefore leaves [loadStateFlow] untouched),
     * `endOfPaginationReached = true` can only come from a real load (the [PagingSource][androidx.paging.PagingSource]
     * reported `nextKey = null`), so it's a reliable, real signal that this generation has
     * genuinely settled — a good point to reconcile [listData] even if the diff that produced it
     * was zero-change (e.g. a second consecutive empty page) and therefore never reached
     * [syncListData] via the [differ]'s [ListUpdateCallback]. See [syncListData].
     */
    private fun startObservingLoadState() {
        Log.d("Start observing load state - $mPagingProgressItem", TAG)
        loadStateJob?.cancel()
        loadStateJob = pagingScope.launch {
            loadStateFlow.collectLatest { loadState ->
                Log.d("Change state — $loadState, progressItem=$mPagingProgressItem", TAG)
                val append = loadState.append
                if (append is LoadState.NotLoading && append.endOfPaginationReached) {
                    Log.d("Change state — endOfPaginationReached, forcing syncListData()", TAG)
                    syncListData()
                }
                if (mPagingProgressItem == null) {
                    Log.d("Change state — skip, mPagingProgressItem is null", TAG)
                    return@collectLatest
                }
                val shouldShow = loadState.append is LoadState.Loading
                if (shouldShow == isProgressVisible) {
                    Log.d(
                        "Change state — skip, shouldShow=$shouldShow already == isProgressVisible",
                        TAG
                    )
                    return@collectLatest
                }
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
        Log.d("onAttachedToRecyclerView — mPagingProgressItem=$mPagingProgressItem", TAG)
        super.onAttachedToRecyclerView(recyclerView)
        if (mPagingProgressItem != null) startObservingLoadState()
    }

    /**
     * Cancels the [loadStateFlow] observer and the [pagingScope] to prevent coroutine leaks,
     * then delegates to [FlexibleAdapter] for its own cleanup.
     */
    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        Log.d("onDetachedFromRecyclerView", TAG)
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
    override fun onLoadMore(position: Int) {
        Log.d("onLoadMore — no-op, position=$position", TAG)
    }

    /**
     * No-op. Data insertion after a page load is handled entirely by [AsyncPagingDataDiffer]
     * via [syncListData]; calling this method directly would corrupt the differ's state.
     */
    override fun onLoadMoreComplete(newItems: List<T>, delay: Long) {
        Log.d("onLoadMoreComplete — no-op, newItems=${newItems.size}, delay=$delay", TAG)
    }
}
