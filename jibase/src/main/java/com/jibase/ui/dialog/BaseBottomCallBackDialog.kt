package com.jibase.ui.dialog

import androidx.annotation.LayoutRes

@Deprecated("Use BaseBottomDialog")
abstract class BaseBottomCallBackDialog<CallBack : Any>(@LayoutRes layoutId: Int) :
    BaseBottomDialog(layoutId) {
    private var internalCallBack: CallBack? = null

    fun setCallBack(callback: CallBack): BaseBottomCallBackDialog<CallBack> {
        this.internalCallBack = callback
        return this
    }

    fun getCallBack(): CallBack? {
        return internalCallBack
    }
}