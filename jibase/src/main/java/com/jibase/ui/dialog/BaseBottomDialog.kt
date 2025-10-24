package com.jibase.ui.dialog

import android.app.Dialog
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.LayoutRes
import androidx.annotation.StyleRes
import androidx.appcompat.view.ContextThemeWrapper
import androidx.fragment.app.FragmentManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.jibase.R
import androidx.core.graphics.drawable.toDrawable

abstract class BaseBottomDialog(
    @LayoutRes private val layoutId: Int,
    @StyleRes
    private val theme: Int = 0
) : BottomSheetDialogFragment() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return if (layoutId > 0) {
            val contextThemeWrapper = ContextThemeWrapper(context, theme)
            inflater.cloneInContext(contextThemeWrapper).inflate(layoutId, container, false)
        } else {
            null
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.run {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.also {
                it.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                doOnWindow(it)
            }
            setOnShowListener { dialog ->
                val d = dialog as BottomSheetDialog

                val bottomSheet =
                    d.findViewById<View>(R.id.design_bottom_sheet) ?: return@setOnShowListener

                BottomSheetBehavior.from(bottomSheet).run {
                    doOnSheetBehavior(this)
                }
            }
        }
        return dialog
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(savedInstanceState)
    }

    open fun setStyle() {
        setStyle(STYLE_NORMAL, initStyle())
    }

    open fun initStyle(): Int {
        return R.style.style_transparent_bottom_sheet
    }

    open fun isShowFullDialog(): Boolean = false

    open fun isDraggable(): Boolean = true

    open fun doOnWindow(window: Window) {}

    open fun doOnSheetBehavior(behavior: BottomSheetBehavior<View>) {
        if (isShowFullDialog()) {
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
        }

        behavior.isDraggable = isDraggable()
    }

    abstract fun onViewReady(savedInstanceState: Bundle?)

    fun show(fragmentManager: FragmentManager): Boolean {
        try {
            if (fragmentManager.findFragmentByTag(javaClass.name) != null) return false
            show(fragmentManager, javaClass.name)
            return true
        } catch (e: Exception) {
            return false
        }
    }
}