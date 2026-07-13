package com.jibase.view

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.CompoundButton
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.core.widget.ImageViewCompat
import androidx.core.widget.TextViewCompat
import com.jibase.R
import com.jibase.databinding.LayoutOptionItemBinding
import com.jibase.extensions.getDimensionPixelOffset
import com.jibase.extensions.gone
import com.jibase.extensions.setLayoutParams
import com.jibase.extensions.visible

@Suppress("MemberVisibilityCanBePrivate", "unused", "JoinDeclarationAndAssignment")
class OptionItemView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    def: Int = 0
) : ConstraintLayout(context, attrs, def) {
    private val binding: LayoutOptionItemBinding

    enum class Type(val value: Int) {
        NONE(-1),
        SWITCH(0),
        TEXT(1),
        IMAGE(2);

        companion object {
            fun safe(v: Int?): Type {
                return entries.find { it.value == v } ?: NONE
            }
        }
    }

    init {
        binding = LayoutOptionItemBinding.inflate(
            LayoutInflater.from(context),
            this
        )

        minHeight = context.getDimensionPixelOffset(R.dimen.min_height_option)


        // load attr
        attrs?.also { attr ->
            val typeArray =
                context.obtainStyledAttributes(attr, R.styleable.OptionItemView, 0, 0)

            setTitle(typeArray.getString(R.styleable.OptionItemView_ot_title))

            setTextAppeared(
                binding.tvTitle,
                typeArray.getResourceId(R.styleable.OptionItemView_ot_title_text_appearance, -1)
            )

            val stateTitleColor =
                typeArray.getColorStateList(R.styleable.OptionItemView_ot_title_color)
            if (stateTitleColor != null) {
                setTitleColor(stateTitleColor)
            } else {
                setTitleColor(typeArray.getColor(R.styleable.OptionItemView_ot_title_color, 0))
            }

            setDesc(typeArray.getString(R.styleable.OptionItemView_ot_desc))
            setTextAppeared(
                binding.tvDesc,
                typeArray.getResourceId(R.styleable.OptionItemView_ot_desc_text_appearance, -1)
            )

            val stateDescColor =
                typeArray.getColorStateList(R.styleable.OptionItemView_ot_desc_color)
            if (stateDescColor != null) {
                setDescColor(stateDescColor)
            } else {
                setDescColor(typeArray.getColor(R.styleable.OptionItemView_ot_desc_color, 0))
            }

            setTextData(typeArray.getString(R.styleable.OptionItemView_ot_text_data))
            setTextAppeared(
                binding.tvText,
                typeArray.getResourceId(R.styleable.OptionItemView_ot_text_data_text_appearance, -1)
            )

            val stateTextDataColor =
                typeArray.getColorStateList(R.styleable.OptionItemView_ot_text_data_color)
            if (stateTextDataColor != null) {
                setTextDataColor(stateTextDataColor)
            } else {
                setTextDataColor(typeArray.getColor(R.styleable.OptionItemView_ot_text_data_color, 0))
            }

            setIcon(
                typeArray.getResourceId(
                    R.styleable.OptionItemView_ot_icon,
                    0
                )
            )
            setIconSize(
                typeArray.getDimensionPixelOffset(
                    R.styleable.OptionItemView_ot_icon_size,
                    -1
                )
            )
            setIconGravity(
                typeArray.getInt(R.styleable.OptionItemView_ot_icon_gravity, -1)
            )
            val stateIconColor =
                typeArray.getColorStateList(R.styleable.OptionItemView_ot_icon_tint)
            if (stateIconColor != null) {
                setIconTint(stateIconColor)
            } else {
                setIconTint(typeArray.getColor(R.styleable.OptionItemView_ot_icon_tint, 0))
            }


            setEndIcon(
                typeArray.getResourceId(
                    R.styleable.OptionItemView_ot_end_icon,
                    0
                )
            )
            setEndIconSize(
                typeArray.getDimensionPixelOffset(
                    R.styleable.OptionItemView_ot_end_icon_size,
                    -1
                )
            )

            val stateEndIconColor =
                typeArray.getColorStateList(R.styleable.OptionItemView_ot_end_icon_tint)
            if (stateEndIconColor != null) {
                setEndIconTint(stateEndIconColor)
            } else {
                setEndIconTint(typeArray.getColor(R.styleable.OptionItemView_ot_end_icon_tint, 0))
            }

            setSwitchButton(
                typeArray.getResourceId(
                    R.styleable.OptionItemView_ot_switch_button,
                    -1
                )
            )

            setType(Type.safe(typeArray.getInt(R.styleable.OptionItemView_ot_type, -1)))

            updateLineUI(
                typeArray.getBoolean(R.styleable.OptionItemView_ot_show_line, true),
                typeArray.getDimensionPixelOffset(R.styleable.OptionItemView_ot_line_margin, -1),
                typeArray.getBoolean(R.styleable.OptionItemView_ot_line_fill_width, false),
                typeArray.getColor(R.styleable.OptionItemView_ot_line_color, Color.TRANSPARENT)
            )
            typeArray.recycle()
        }
    }

    fun setImageData(res: Int) {
        binding.imageData.setImageResource(res)
    }

    fun setTextData(text: String?) {
        binding.tvText.text = text
    }

    fun setTextDataColor(color: ColorStateList?) {
        if (color == null) return
        binding.tvText.setTextColor(color)
    }

    fun setTextDataColor(color: Int) {
        if (color == 0) return
        binding.tvText.setTextColor(color)
    }

    fun setSwitchEnable(enable: Boolean) {
        binding.sw.isEnabled = enable
    }

    fun setChecked(checked: Boolean) {
        binding.sw.isChecked = checked
    }

    fun isChecked() = binding.sw.isChecked

    fun setOnCheckedChanged(action: (CompoundButton, Boolean) -> Unit) {
        binding.sw.setOnCheckedChangeListener { buttonView, isChecked ->
            action.invoke(buttonView, isChecked)
        }
    }

    fun setTitle(title: String?) {
        binding.tvTitle.text = title.orEmpty()
    }

    fun setTitleColor(color: ColorStateList?) {
        if (color == null) return
        binding.tvTitle.setTextColor(color)
    }

    fun setTitleColor(color: Int) {
        if (color == 0) return
        binding.tvTitle.setTextColor(color)
    }

    fun setDesc(text: String?) {
        if (!text.isNullOrBlank()) {
            binding.tvDesc.visible()
            binding.tvDesc.text = text
        }
    }


    fun setDescColor(color: ColorStateList?) {
        if (color == null) return
        binding.tvDesc.setTextColor(color)
    }

    fun setDescColor(color: Int) {
        if (color == 0) return
        binding.tvDesc.setTextColor(color)
    }

    fun setTextAppeared(textView: TextView, styleRes: Int) {
        if (styleRes <= 0) return
        TextViewCompat.setTextAppearance(textView, styleRes)
    }

    fun setIcon(iconRes: Int) {
        with(binding.imageIcon) {
            if (iconRes <= 0) {
                gone()
            } else {
                visible()
                setImageResource(iconRes)
            }
        }
    }

    fun setIconSize(size: Int) {
        if (size <= 0) return
        binding.imageIcon.setLayoutParams(width = size, height = size)
    }

    fun setIconGravity(gravity: Int) {
        if (gravity == -1) return
        binding.imageIcon.updateLayoutParams<LayoutParams> {
            when (gravity) {
                0 -> {
                    topToTop = R.id.ll
                    bottomToBottom = LayoutParams.UNSET
                }

                1 -> {
                    topToTop = LayoutParams.PARENT_ID
                    bottomToBottom = LayoutParams.PARENT_ID
                }

                2 -> {
                    topToTop = R.id.ll
                    bottomToBottom = R.id.ll
                }
            }
        }
    }

    fun setIconTint(color: Int) {
        with(binding.imageIcon) {
            if (color == 0) {
                clearColorFilter()
            } else {
                setColorFilter(color)
            }
        }
    }

    fun setIconTint(color: ColorStateList?) {
        with(binding.imageIcon) {
            if (color == null) {
                clearColorFilter()
            } else {
                ImageViewCompat.setImageTintList(this, color)
            }
        }
    }

    fun setEndIcon(iconRes: Int) {
        with(binding.imageEndIcon) {
            if (iconRes <= 0) {
                gone()
            } else {
                visible()
                setImageResource(iconRes)
            }
        }
    }

    fun setEndIconSize(size: Int) {
        if (size <= 0) return
        binding.imageEndIcon.setLayoutParams(width = size, height = size)
    }

    fun setEndIconTint(color: Int) {
        with(binding.imageEndIcon) {
            if (color == 0) {
                clearColorFilter()
            } else {
                setColorFilter(color)
            }
        }
    }


    fun setEndIconTint(color: ColorStateList?) {
        with(binding.imageEndIcon) {
            if (color == null) {
                clearColorFilter()
            } else {
                ImageViewCompat.setImageTintList(this, color)
            }
        }
    }

    fun setType(type: Type) {
        when (type) {
            Type.SWITCH -> {
                binding.flEnd.visible()
                binding.sw.visible()
            }

            Type.TEXT -> {
                binding.flEnd.visible()
                binding.tvText.visible()
            }

            Type.IMAGE -> {
                binding.flEnd.visible()
                binding.imageData.visible()
            }

            else -> {
                binding.flEnd.gone()
            }
        }
    }

    fun setSwitchButton(res: Int) {
        if (res <= 0) return
        with(binding.sw) {
            setButtonDrawable(res)
            thumbDrawable = null
            background = null
        }
    }

    fun updateLineUI(visible: Boolean, margin: Int, fillWidth: Boolean, tintColor: Int) {
        binding.line.apply {
            isVisible = visible
            if (visible) {
                if (tintColor != 0) {
                    alpha = 1f
                    backgroundTintList = ColorStateList.valueOf(tintColor)
                } else {
                    backgroundTintList = null
                    alpha = 0.2f
                }
            }
            updateLayoutParams<LayoutParams> {
                setMargins(left, margin, right, bottom)
                if (fillWidth) {
                    startToStart = LayoutParams.PARENT_ID
                }
            }
        }
    }
}