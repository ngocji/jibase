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
import androidx.core.graphics.drawable.toDrawable
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.jibase.R

abstract class BaseDialog(
    @LayoutRes
    private val layoutId: Int,
    @StyleRes
    private val theme: Int = 0
) : DialogFragment() {
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle()
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        dialog.run {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.also {
                it.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                doOnWindow(it)
            }
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog
        if (dialog != null) {
            if (isShowFullDialog()) {
                val width = ViewGroup.LayoutParams.MATCH_PARENT
                val height = ViewGroup.LayoutParams.MATCH_PARENT
                dialog.window?.setLayout(width, height)
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(savedInstanceState)
    }

    open fun setStyle() {
        setStyle(STYLE_NO_TITLE, initStyle())
    }

    open fun initStyle(): Int {
        return R.style.style_dialog_90
    }

    open fun isShowFullDialog() = false

    abstract fun onViewReady(savedInstanceState: Bundle?)

    open fun doOnWindow(window: Window) {}

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