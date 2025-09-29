package com.jibase.iflexible.helpers

import android.view.Menu
import android.view.MenuItem
import androidx.annotation.MenuRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.ActionMode
import androidx.recyclerview.widget.RecyclerView.NO_POSITION
import com.jibase.iflexible.adapter.FlexibleAdapter.Companion.IDLE
import com.jibase.iflexible.adapter.FlexibleAdapter.Companion.MULTI
import com.jibase.iflexible.adapter.FlexibleAdapter.Companion.SINGLE
import com.jibase.iflexible.adapter.FlexibleAdapter
import com.jibase.utils.Log

class ActionModeHelper(
        val targetActivity: AppCompatActivity,
        val adapter: FlexibleAdapter<*>,
        @MenuRes val cabMenu: Int,
        val callback: ActionModeListener? = null) : ActionMode.Callback {

    private val TAG = javaClass.simpleName

    private var defaultMode = IDLE
    private var disableSwipe = false
    private var disableDrag = false
    private var longPressDragDisableByHelper = false
    private var dragDisableByHelper = false
    private var swipeDisableByHelper = false
    private var finishIfNoneSelect = false
    private var finishToClickActionItem = false
    private var enableActionModeLongPress = false

    private var mActionMode: ActionMode? = null

    /*----------------*/
    /*     CONFIG     */
    /*----------------*/
    /**
     * Changes the default mode to apply when the ActionMode is destroyed and normal selection is
     * again active.
     * <p>Default value is {@link Mode#IDLE}.</p>
     *
     * @param defaultMode the new default mode when ActionMode is off, accepted values:
     *                    {@code IDLE, SINGLE}
     * @return this object, so it can be chained
     */
    fun withDefaultMode(defaultMode: Int): ActionModeHelper {
        if (defaultMode == IDLE || defaultMode == SINGLE)
            this.defaultMode = defaultMode
        return this
    }

    fun enableFinishIfNoneSelected(finish: Boolean): ActionModeHelper {
        this.finishIfNoneSelect = finish
        return this
    }

    fun enableFinishToClickActionItem(finish: Boolean): ActionModeHelper {
        this.finishToClickActionItem = finish
        return this
    }

    fun enableActionModeWhenLongPress(enable: Boolean): ActionModeHelper {
        this.enableActionModeLongPress = enable
        return this
    }

    /**
     * Automatically disables LongPress drag and Handle drag capability when ActionMode is
     * activated and enable it again when ActionMode is destroyed.
     *
     * @param disableDrag true to disable the drag, false to maintain the drag during ActionMode
     * @return this object, so it can be chained
     */
    fun disableDragOnActionMode(disableDrag: Boolean): ActionModeHelper {
        this.disableDrag = disableDrag
        return this
    }

    /**
     * Automatically disables Swipe capability when ActionMode is activated and enable it again
     * when ActionMode is destroyed.
     *
     * @param disableSwipe true to disable the swipe, false to maintain the swipe during ActionMode
     * @return this object, so it can be chained
     */

    fun disableSwipeOnActionMode(disableSwipe: Boolean): ActionModeHelper {
        this.disableSwipe = disableSwipe
        return this
    }

    /*---------------------*/
    /*     MAIN METHOD     */
    /*---------------------*/

    fun getActionMode(): ActionMode? {
        return mActionMode
    }

    fun isActionModeStarted() = mActionMode != null

    /**
     * Gets the activated position only when mode is [SINGLE].
     *
     * @return the activated position when [SINGLE]. -1 if no item is selected
     */
    fun getActivatePosition(): Int {
        val selectedPositions = adapter.getSelectedPositions()
        if (adapter.mode == SINGLE && selectedPositions.size == 1) {
            return selectedPositions.first()
        }
        return NO_POSITION
    }

    /**
     * Implements the basic behavior of a CAB and multi select behavior.
     *
     * @param position the current item position
     * @return true if selection is changed, false if the click event should ignore the ActionMode
     * and continue
     */

    fun onItemClick(position: Int): Boolean {
        if (position != NO_POSITION) {
            toggleSelection(position)
            return true
        }
        return false
    }

    /**
     * Implements the basic behavior of a CAB and multi select behavior onLongClick.
     *
     * @param activity the current Activity
     * @param position the position of the clicked item
     * @return the initialized ActionMode or null if nothing was done
     */
    fun onItemLongClick(position: Int): ActionMode? {
        if (enableActionModeLongPress) startActionMode()
        toggleSelection(position)
        return mActionMode
    }


    fun toggleSelection(position: Int) {
        if (adapter.hasPosition(position) && (adapter.mode == SINGLE && !adapter.isSelected(position) || adapter.mode == MULTI)) {
            adapter.toggleSelection(position)
        }

        mActionMode?.also {
            val count = adapter.getSelectedItemCount()
            if (count == 0 && finishIfNoneSelect) {
                it.finish()
            } else {
                updateContextTitle(count)
            }
        }
    }

    fun selectAll(vararg viewTypes: Int) {
        adapter.selectAll(*viewTypes)
        updateContextTitle(adapter.getSelectedItemCount())
    }


    private fun updateContextTitle(count: Int) {
        if (callback != null) {
            callback.onUpdateSelectionTitle(mActionMode, count)
        } else {
            mActionMode?.title = count.toString()
        }
    }

    fun startActionMode() {
        if (mActionMode == null) {
            mActionMode = targetActivity.startSupportActionMode(this)
        }
        updateContextTitle(0)
    }

    fun destroyActionModeIfCan(): Boolean {
        if (mActionMode != null) {
            mActionMode?.finish()
            return true
        }
        return false
    }

    /*----------------------------------------------*/
    /*     OVERRIDE ACTION MODE CALLBACK METHOD     */
    /*----------------------------------------------*/

    override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        mode?.apply {
            menuInflater.inflate(cabMenu, menu)
        }
        Log.d("ActionMode is active!", TAG)
        adapter.setMode(MULTI)
        updateActionModeState(true)
        disableSwipeDragCapabilities()
        return callback == null || callback.onCreateActionMode(mode, menu)
    }


    override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
        return callback != null && callback.onPrepareActionMode(mode, menu)
    }

    override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
        val consumed = callback?.onActionItemClicked(mode, item) ?: false

        if (!consumed && finishToClickActionItem) {
            // Finish the actionMode
            mode?.finish()
        }
        return consumed
    }

    override fun onDestroyActionMode(mode: ActionMode?) {
        Log.d("ActionMode is about to be destroyed!", TAG)
        // Change mode and deselect everything
        adapter.setMode(defaultMode)
        adapter.clearSelection()
        updateActionModeState(false)
        mActionMode = null
        // Re-enable Swipe and Drag capabilities if they were disabled by this helper
        enableSwipeDragCapabilities()
        // Notify the provided callback
        callback?.onDestroyActionMode(mode)
    }

    /*-------------------------*/
    /*     PRIVATE METHOD     */
    /*-----------------------*/

    private fun enableSwipeDragCapabilities() {
        if (longPressDragDisableByHelper) {
            longPressDragDisableByHelper = false
            adapter.setLongPressDragEnabled(true)
        }
        if (dragDisableByHelper) {
            dragDisableByHelper = false
            adapter.setHandleDragEnabled(true)
        }
        if (swipeDisableByHelper) {
            swipeDisableByHelper = false
            adapter.setSwipeEnabled(true)
        }
    }

    private fun disableSwipeDragCapabilities() {
        if (disableDrag && adapter.isLongPressDragEnabled()) {
            longPressDragDisableByHelper = true
            adapter.setLongPressDragEnabled(false)
        }
        if (disableDrag && adapter.isHandleDragEnabled()) {
            dragDisableByHelper = true
            adapter.setHandleDragEnabled(false)
        }
        if (disableSwipe && adapter.isSwipeEnabled()) {
            swipeDisableByHelper = true
            adapter.setSwipeEnabled(false)
        }
    }

    private fun updateActionModeState(enable: Boolean) {
        adapter.isActionModeStateEnable = enable
    }

    interface ActionModeListener : ActionMode.Callback {
        fun onUpdateSelectionTitle(mode: ActionMode?, count: Int) {
            mode?.title = count.toString()
        }

        override fun onCreateActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return true
        }

        override fun onPrepareActionMode(mode: ActionMode?, menu: Menu?): Boolean {
            return true
        }

        override fun onActionItemClicked(mode: ActionMode?, item: MenuItem?): Boolean {
            return true
        }

        override fun onDestroyActionMode(mode: ActionMode?) {

        }
    }
}