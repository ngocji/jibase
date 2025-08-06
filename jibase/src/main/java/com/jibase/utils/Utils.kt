package com.jibase.utils

import android.content.Context
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Menu
import android.widget.TextView

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
}