package com.jibase.utils

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Menu
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.jibase.BuildConfig

object Utils {
    fun getColorByAttr(context: Context, attrId: Int): Int {
        val typedValue = TypedValue()
        val theme = context.theme
        theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    fun tintMenu(color: Int, menu: Menu?) {
        if (menu == null || menu.size() <= 0) return
        for (i in 0 until menu.size()) {
            val item = menu.getItem(i)
            if (item.icon != null) {
                tintDrawable(color, item.icon)
            }
        }
    }

    fun tintMenu(color: Int, menu: Menu?, id: Int) {
        if (menu == null || menu.size() <= 0) return
        val item = menu.findItem(id)
        if (item != null && item.icon != null) {
            tintDrawable(color, item.icon)
        }
    }

    fun strikeThought(view: TextView, enable: Boolean) {
        if (enable) {
            view.paintFlags = view.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            view.paintFlags = view.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        }
    }

    fun changeMenuText(menu: Menu, id: Int, text: Int) {
        val menuItem = menu.findItem(id)
        menuItem?.setTitle(text)
    }

    fun changeMenuVisible(menu: Menu, visible: Boolean, vararg id: Int) {
        id.forEach {
            val menuItem = menu.findItem(it)
            menuItem?.isVisible = visible
        }
    }

    fun changeMenuIcon(menu: Menu?, color: Int, itemId: Int, icon: Int) {
        menu?.findItem(itemId)?.setIcon(icon)
        tintMenu(color, menu, itemId)
    }

    fun tintDrawable(color: Int, vararg drawable: Drawable?) {
        for (d in drawable) {
            d?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    fun tintStrokeDrawable(color: Int, background: Drawable) {
        if (background is GradientDrawable) {
            background.setStroke(3, color)
        }
    }

    fun setStatusBarColor(activity: Activity, @ColorRes color: Int) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = ContextCompat.getColor(activity, color)
    }

    fun setStatusBarColorInt(activity: Activity, color: Int) {
        val window = activity.window
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = color
    }

    fun setAppearanceLightStatusBar(activity: Activity, enable: Boolean) {
        kotlin.runCatching {
            val windowInsetsController =
                WindowCompat.getInsetsController(activity.window, activity.window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = enable
        }
    }

    @SuppressLint("WrongConstant")
    fun setSystemBarsVisibility(
        activity: Activity,
        visible: Boolean,
        @WindowInsetsCompat.Type.InsetsType type: Int,
        behavior: Int = 1 // default
    ) {
        setSystemBarsVisibility(activity.window, visible, type, behavior)
    }

    @SuppressLint("WrongConstant")
    fun setSystemBarsVisibility(
        window: Window?,
        visible: Boolean,
        @WindowInsetsCompat.Type.InsetsType type: Int,
        behavior: Int = 1 // default
    ) {
        window ?: return
        kotlin.runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowCompat.setDecorFitsSystemWindows(window, false)

                val controller =  WindowInsetsControllerCompat(window, window.decorView)

                if (visible) {
                    controller.show(type)
                } else {
                    controller.hide(type)
                    controller.systemBarsBehavior = behavior
                }
            } else {
                @Suppress("DEPRECATION")
                val decorView = window.decorView
                decorView.systemUiVisibility = when {
                    visible -> View.SYSTEM_UI_FLAG_VISIBLE

                    type == WindowInsetsCompat.Type.statusBars() -> View.SYSTEM_UI_FLAG_FULLSCREEN

                    type == WindowInsetsCompat.Type.navigationBars() -> View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY

                    else -> View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                }
            }
        }
    }

    fun setWindowNoLimit(activity: Activity?) {
        setWindowNoLimit(activity?.window)
    }

    fun setWindowNoLimit(window: Window?) {
        window?.setFlags(
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
        )
    }

    fun clearWindowNoLimit(activity: Activity?) {
        val window = activity?.window
        clearWindowNoLimit(window)
    }

    fun clearWindowNoLimit(window: Window?) {
        window ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // API 30+ (Android 11+):
            WindowCompat.setDecorFitsSystemWindows(window, true)
        } else {
            // API < 30: clear
            window.clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
        }
    }

    fun getLibVersion() = BuildConfig.VERSION
}