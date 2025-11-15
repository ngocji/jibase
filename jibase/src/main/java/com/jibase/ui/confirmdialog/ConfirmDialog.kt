package com.jibase.ui.confirmdialog

import android.content.Context
import android.content.DialogInterface
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.Window
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.GravityInt
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.updateLayoutParams
import androidx.core.widget.TextViewCompat
import com.google.android.material.button.MaterialButton
import com.jibase.R
import com.jibase.databinding.DialogConfirmBinding
import com.jibase.extensions.gone
import com.jibase.extensions.viewBinding
import com.jibase.extensions.visible
import com.jibase.ui.dialog.BaseDialog

class ConfirmDialog(@StyleRes theme: Int) : BaseDialog(R.layout.dialog_confirm, theme) {

    companion object {
        fun newBuilder(context: Context, theme: Int): Builder {
            return Builder(context, theme)
        }
    }

    private val binding by viewBinding(DialogConfirmBinding::bind)
    private var isNotifyConfirm = false
    private var builder: Builder? = null

    override fun initStyle(): Int {
        return R.style.style_dialog_80
    }

    override fun onViewReady(savedInstanceState: Bundle?) {
        builder?.also {
            applyConfigDialog(it.config)

            applyTextConfig(binding.dialogTitle, it.title)
            applyTextConfig(binding.dialogSubTitle, it.subTitle)
            applyTextConfig(binding.dialogMessage, it.message)

            applyButtonConfig(binding.buttonConfirm, it.buttonConfirm)
            applyButtonConfig(binding.buttonCancel, it.buttonCancel)
        }

        binding.buttonConfirm.setOnClickListener { onConfirmClicked() }
        binding.buttonCancel.setOnClickListener { onCancelClicked() }
        binding.buttonClose.setOnClickListener { onCancelClicked() }
    }

    override fun doOnWindow(window: Window) {
        builder?.doOnWindowCallback?.invoke(window)
    }

    private fun onCancelClicked() {
        if (isDismissWhenClick()) {
            dismissAllowingStateLoss()
        }
        getCallBack()?.onCancelClicked(this)
    }

    private fun onConfirmClicked() {
        isNotifyConfirm = true
        if (isDismissWhenClick()) {
            dismissAllowingStateLoss()
        }
        getCallBack()?.onConfirmClicked(this)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        if (!isNotifyConfirm) getCallBack()?.onDismiss()
    }

    private fun applyConfigDialog(config: ConfigDialog?) {
        config ?: return
        config.icon?.also {
            with(binding.dialogIcon) {
                visible()
                setImageBitmap(it)

                if (config.iconTint != 0) {
                    setColorFilter(config.iconTint)
                }

                updateLayoutParams<LinearLayout.LayoutParams> {
                    if (config.iconSize != 0) {
                        height = config.iconSize
                        width = config.iconSize
                    }
                    if (config.iconGravity != Gravity.CENTER) {
                        gravity = config.iconGravity
                    }
                }
            }
        }

        isCancelable = config.isCancelable

        when {
            config.backgroundResource > 0 -> binding.dialogBackground.setBackgroundResource(config.backgroundResource)
            config.background != null -> binding.dialogBackground.background = config.background
        }
    }

    private fun applyTextConfig(textView: TextView, configText: ConfigText) {
        with(textView) {
            if (configText.text.isNullOrBlank()) {
                gone()
            } else {
                visible()
                text = configText.text

                when {
                    configText.textStyle > 0 -> TextViewCompat.setTextAppearance(
                        this,
                        configText.textStyle
                    )

                    configText.textColor != null -> setTextColor(configText.textColor ?: 0)
                }

                if (configText.textGravity != Gravity.CENTER) {
                    updateLayoutParams<LinearLayout.LayoutParams> {
                        gravity = configText.textGravity
                    }
                }
            }
        }
    }

    private fun applyButtonConfig(button: MaterialButton, configButton: ConfigButton) {
        with(button) {
            if (!configButton.isShow) {
                gone()
                return
            }

            visible()

            applyButtonText(button, configButton)

            applyButtonIcon(button, configButton)

            applyButtonBackground(button, configButton)
        }
    }

    private fun applyButtonText(button: MaterialButton, configButton: ConfigButton) {
        with(button) {
            configButton.text?.also { text = it }
            isAllCaps = configButton.textAllCaps
            when {
                configButton.textStyle > 0 -> TextViewCompat.setTextAppearance(
                    this,
                    configButton.textStyle
                )

                configButton.textColor != null -> {
                    setTextColor(configButton.textColor ?: 0)
                }
            }
        }
    }

    private fun applyButtonIcon(button: MaterialButton, configButton: ConfigButton) {
        fun updateIconGravity() {
            if (configButton.iconGravity != MaterialButton.ICON_GRAVITY_TEXT_START) {
                button.iconGravity = configButton.iconGravity
            }
        }

        with(button) {
            when {
                configButton.iconRes > 0 -> {
                    setIconResource(configButton.iconRes)
                    updateIconGravity()
                }

                configButton.icon != null -> {
                    icon = configButton.icon
                    updateIconGravity()
                }
            }

            if (configButton.iconSize > 0) {
                iconSize = configButton.iconSize
            }

            if (configButton.iconTint != 0) {
                iconTint = ColorStateList.valueOf(configButton.iconTint)
            }
        }
    }

    private fun applyButtonBackground(button: MaterialButton, configButton: ConfigButton) {
        with(button) {
            when {
                configButton.backgroundColor != 0x0 -> {
                    backgroundTintList = ColorStateList.valueOf(configButton.backgroundColor)
                }

                configButton.background != null -> {
                    background = configButton.background
                }

                else -> {
                    backgroundTintList = ColorStateList.valueOf(0)
                }
            }
        }
    }

    private fun getCallBack(): CallBack? {
        return builder?.config?.callBack
    }

    private fun isDismissWhenClick() = builder?.config?.dismissWhenClick ?: true


    interface CallBack {
        fun onConfirmClicked(dialog: ConfirmDialog?) {}
        fun onCancelClicked(dialog: ConfirmDialog) {}
        fun onDismiss() {}
        fun doOnWindow(window: Window) {}
    }

    class Builder(
        private val context: Context,
        private val theme: Int
    ) {
        val config = ConfigDialog()
        val title = ConfigText()
        val subTitle = ConfigText()
        val message = ConfigText()
        val buttonConfirm =
            ConfigButton(
                backgroundColor = ContextCompat.getColor(
                    context,
                    com.jibase.R.color.colorAccent
                )
            )
        val buttonCancel = ConfigButton()

        var doOnWindowCallback: ((Window) -> Unit)? = null

        // config
        fun setIcon(@DrawableRes res: Int): Builder {
            config.icon = ContextCompat.getDrawable(context, res)?.toBitmap()
            return this
        }

        fun setIcon(bitmap: Bitmap?): Builder {
            config.icon = bitmap
            return this
        }

        fun setIconSize(size: Int): Builder {
            config.iconSize = size
            return this
        }

        fun setIconGravity(@GravityInt gravity: Int): Builder {
            config.iconGravity = gravity
            return this
        }

        fun setIconTintColor(@ColorInt color: Int): Builder {
            config.iconTint = color
            return this
        }

        fun setIconTintRes(@ColorRes color: Int): Builder {
            return setIconTintColor(ContextCompat.getColor(context, color))
        }

        fun setCloseIcon(@DrawableRes res: Int): Builder {
            config.closeIcon = ContextCompat.getDrawable(context, res)?.toBitmap()
            return this
        }

        fun setCloseIcon(bitmap: Bitmap?): Builder {
            config.closeIcon = bitmap
            return this
        }

        fun setCancelable(isCancelable: Boolean): Builder {
            config.isCancelable = isCancelable
            return this
        }

        fun setDismissWhenClick(dismiss: Boolean): Builder {
            config.dismissWhenClick = dismiss
            return this
        }

        fun setCallBack(callBack: CallBack): Builder {
            config.callBack = callBack
            return this
        }

        fun setBackgroundResource(@DrawableRes res: Int): Builder {
            config.backgroundResource = res
            return this
        }

        fun setBackground(@ColorRes color: Int): Builder {
            config.background = ColorDrawable(ContextCompat.getColor(context, color))
            return this
        }

        fun setBackgroundColor(@ColorInt color: Int): Builder {
            config.background = ColorDrawable(color)
            return this
        }

        fun setBackground(drawable: Drawable?): Builder {
            config.background = drawable
            return this
        }


        // title

        fun setTitle(@StringRes res: Int): Builder {
            title.text = context.getString(res)
            return this
        }

        fun setTitle(text: String): Builder {
            title.text = text
            return this
        }

        fun setTitleColorRes(@ColorRes color: Int): Builder {
            title.textColor = ContextCompat.getColor(context, color)
            return this
        }

        fun setTitleColor(@ColorInt color: Int): Builder {
            title.textColor = color
            return this
        }

        fun setTitleStyle(@StyleRes style: Int): Builder {
            title.textStyle = style
            return this
        }

        fun setTitleGravity(@GravityInt gravity: Int): Builder {
            title.textGravity = gravity
            return this
        }


        // subtitle
        fun setSubTitle(@StringRes res: Int): Builder {
            subTitle.text = context.getString(res)
            return this
        }

        fun setSubTitle(text: String): Builder {
            subTitle.text = text
            return this
        }

        fun setSubTitleColorRes(@ColorRes color: Int): Builder {
            subTitle.textColor = ContextCompat.getColor(context, color)
            return this
        }

        fun setSubTitleColor(@ColorInt color: Int): Builder {
            subTitle.textColor = color
            return this
        }

        fun setSubTitleStyle(@StyleRes style: Int): Builder {
            subTitle.textStyle = style
            return this
        }

        fun setSubTitleGravity(@GravityInt gravity: Int): Builder {
            subTitle.textGravity = gravity
            return this
        }


        // message
        fun setMessage(@StringRes res: Int): Builder {
            message.text = context.getString(res)
            return this
        }

        fun setMessage(text: String): Builder {
            message.text = text
            return this
        }

        fun setMessageColorRes(@ColorRes color: Int): Builder {
            message.textColor = ContextCompat.getColor(context, color)
            return this
        }

        fun setMessageColor(@ColorInt color: Int): Builder {
            message.textColor = color
            return this
        }

        fun setMessageStyle(@StyleRes style: Int): Builder {
            message.textStyle = style
            return this
        }

        fun setMessageGravity(@GravityInt gravity: Int): Builder {
            message.textGravity = gravity
            return this
        }


        // confirm button
        fun setConfirmText(@StringRes res: Int): Builder {
            buttonConfirm.text = context.getString(res)
            return this
        }

        fun setConfirmText(text: String): Builder {
            buttonConfirm.text = text
            return this
        }

        fun setConfirmTextColorRes(@ColorRes color: Int): Builder {
            buttonConfirm.textColor = ContextCompat.getColor(context, color)
            return this
        }

        fun setConfirmTextColor(@ColorInt color: Int): Builder {
            buttonConfirm.textColor = color
            return this
        }

        fun setConfirmBackgroundResource(@DrawableRes res: Int): Builder {
            buttonConfirm.background = ContextCompat.getDrawable(context, res)
            return this
        }

        fun setConfirmBackground(@ColorRes color: Int): Builder {
            buttonConfirm.backgroundColor = ContextCompat.getColor(context, color)
            return this
        }

        fun setConfirmBackgroundColor(@ColorInt color: Int): Builder {
            buttonConfirm.backgroundColor = color
            return this
        }

        fun setConfirmBackground(drawable: Drawable?): Builder {
            buttonConfirm.background = drawable
            return this
        }

        fun setConfirmIcon(@DrawableRes res: Int): Builder {
            buttonConfirm.iconRes = res
            return this
        }

        fun setConfirmIcon(drawable: Drawable?): Builder {
            buttonConfirm.icon = drawable
            return this
        }

        fun setConfirmIconTintColor(@ColorInt color: Int): Builder {
            buttonConfirm.iconTint = color
            return this
        }

        fun setConfirmIconTintRes(@ColorRes color: Int): Builder {
            return setConfirmIconTintColor(ContextCompat.getColor(context, color))
        }

        fun setConfirmTextStyle(@StyleRes style: Int): Builder {
            buttonConfirm.textStyle = style
            return this
        }

        fun setConfirmTextGravity(@GravityInt gravity: Int): Builder {
            buttonConfirm.textGravity = gravity
            return this
        }

        fun setConfirmTextAllCaps(isCap: Boolean): Builder {
            buttonConfirm.textAllCaps = isCap
            return this
        }

        fun setShowConfirmButton(show: Boolean): Builder {
            buttonConfirm.isShow = show
            return this
        }


        // cancel button
        fun setCancelText(@StringRes res: Int): Builder {
            buttonCancel.text = context.getString(res)
            return this
        }

        fun setCancelText(text: String): Builder {
            buttonCancel.text = text
            return this
        }

        fun setCancelTextColorRes(@ColorRes color: Int): Builder {
            buttonCancel.textColor = ContextCompat.getColor(context, color)
            return this
        }

        fun setCancelTextColor(@ColorInt color: Int): Builder {
            buttonCancel.textColor = color
            return this
        }

        fun setCancelBackgroundResource(@DrawableRes res: Int): Builder {
            buttonCancel.background = ContextCompat.getDrawable(context, res)
            return this
        }

        fun setCancelBackground(@ColorRes color: Int): Builder {
            buttonCancel.backgroundColor = ContextCompat.getColor(context, color)
            return this
        }

        fun setCancelBackgroundColor(@ColorInt color: Int): Builder {
            buttonCancel.backgroundColor = color
            return this
        }

        fun setCancelBackground(drawable: Drawable?): Builder {
            buttonCancel.background = drawable
            return this
        }

        fun setCancelIcon(@DrawableRes res: Int): Builder {
            buttonCancel.iconRes = res
            return this
        }

        fun setCancelIcon(drawable: Drawable?): Builder {
            buttonCancel.icon = drawable
            return this
        }

        fun setCancelIconTintColor(@ColorInt color: Int): Builder {
            buttonCancel.iconTint = color
            return this
        }

        fun setCancelIconTintRes(@ColorRes color: Int): Builder {
            return setCancelIconTintColor(ContextCompat.getColor(context, color))
        }

        fun setCancelTextStyle(@StyleRes style: Int): Builder {
            buttonCancel.textStyle = style
            return this
        }

        fun setCancelTextGravity(@GravityInt gravity: Int): Builder {
            buttonCancel.textGravity = gravity
            return this
        }

        fun setCancelTextAllCaps(isCap: Boolean): Builder {
            buttonCancel.textAllCaps = isCap
            return this
        }

        fun setShowCancelButton(show: Boolean): Builder {
            buttonCancel.isShow = show
            return this
        }

        fun setDoOnWindowCallback(callback: (Window) -> Unit): Builder {
            doOnWindowCallback = callback
            return this
        }

        fun build(): ConfirmDialog {
            val dialog = ConfirmDialog(theme).apply {
                builder = this@Builder
            }

            return dialog
        }
    }
}
