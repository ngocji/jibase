package com.jibase.ui.confirmdialog

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.view.Gravity
import androidx.annotation.DrawableRes
import androidx.annotation.GravityInt

data class ConfigDialog(
    var icon: Bitmap? = null,
    var iconSize: Int = 0,
    var iconTint: Int = 0,
    @GravityInt
    var iconGravity: Int = Gravity.CENTER,
    var isCancelable: Boolean = true,
    @DrawableRes
    var closeIconResource: Int? = null,
    var dismissWhenClick: Boolean = true,
    var callBack: ConfirmDialog.CallBack? = null,

    var background: Drawable? = null,
    @DrawableRes
    var backgroundResource: Int = 0,
)