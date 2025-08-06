package com.jibase.view

import android.graphics.Color
import android.view.View
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.google.android.material.snackbar.Snackbar
import com.jibase.R

class ISnackBar {
    private val noneColor by lazy { context?.let { ContextCompat.getColor(it, R.color.trans) } ?: Color.TRANSPARENT }

    private var rootView: View ? = null
    private var message: String = ""
    private var actionName: String = ""
    private var duration: Int = Snackbar.LENGTH_SHORT
    private var action: ((v: View) -> Unit)? = null

    /* styling */
    private var messageColor: Int = noneColor
    private var actionColor: Int = noneColor
    private var backgroundColor: Int = noneColor

    private val context get() = rootView?.context


    fun of(rootView: View): ISnackBar {
        this.rootView = rootView
        return this
    }

    fun withMessage(message: String): ISnackBar {
        this.message = message
        return this
    }

    fun withMessage(@StringRes messageResId: Int): ISnackBar {
        this.message = context?.getString(messageResId).orEmpty()
        return this
    }

    fun withActionName(actionName: String): ISnackBar {
        this.actionName = actionName
        return this
    }

    fun withActionName(@StringRes actionNameResId: Int): ISnackBar {
        this.actionName = context?.getString(actionNameResId).orEmpty()
        return this
    }

    fun withDuration(duration: Int): ISnackBar {
        this.duration = duration
        return this
    }

    fun setAction(action: ((v: View) -> Unit)): ISnackBar {
        this.action = action
        return this
    }

    fun setTextColor(messageColor: Int): ISnackBar {
        this.messageColor = messageColor
        return this
    }

    fun setActionColor(actionColor: Int): ISnackBar {
        this.actionColor = actionColor
        return this
    }

    fun setBackgroundColor(backgroundColor: Int): ISnackBar {
        this.backgroundColor = backgroundColor
        return this
    }

    @Throws
    fun show(): Snackbar {
        val snack = create()
        snack.show()
        return snack
    }

    @Throws
    fun create(): Snackbar {
        val root = rootView ?: throw NullPointerException("Empty root view. you want to call of(View v) first")
        return Snackbar.make(root, message, duration).apply {
            //Set background
            if (backgroundColor != noneColor) {
                setBackgroundColor(backgroundColor)
            }
            //Set text color
            if (messageColor != noneColor) {
                view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)?.also {
                    it.setTextColor(messageColor)
                }
            }
            // set action color
            if (actionColor != noneColor) {
                setActionTextColor(actionColor)
            }
            if (actionName.isNotEmpty() && action != null) {
                setAction(actionName) { v -> action?.invoke(v) }
            }
        }
    }
}