package com.jibase.ui.confirmdialog

import android.graphics.drawable.Drawable
import android.view.Gravity
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.GravityInt
import androidx.annotation.StyleRes
import com.google.android.material.button.MaterialButton

data class ConfigButton(
    var text: String? = null,
    var textColor: Int? = null,
    @StyleRes
    var textStyle: Int = 0,
    @GravityInt
    var textGravity: Int = Gravity.CENTER,
    var textAllCaps: Boolean = false,


    var background: Drawable? = null,
    @ColorInt
    var backgroundColor: Int? = null,

    var icon: Drawable? = null,
    @DrawableRes
    var iconRes: Int = 0,
    @GravityInt
    var iconGravity: Int = MaterialButton.ICON_GRAVITY_TEXT_START,
    var iconSize: Int = 0,
    var iconTint: Int = 0,

    var isShow: Boolean = true
)