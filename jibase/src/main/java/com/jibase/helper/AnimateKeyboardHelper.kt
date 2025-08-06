package com.jibase.helper

import android.os.Build
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.Insets
import androidx.core.view.OnApplyWindowInsetsListener
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsAnimationCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import com.jibase.helper.KeyboardHelper
import com.jibase.utils.Log

private fun isSupportAnimation() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

class AnimateKeyboardHelper(
    val activity: AppCompatActivity,
    val rootView: View,
    val subFeatureContent: View,
    val needUpdateSubFeatureContentSizeByKeyboard: Boolean = false,
    vararg animateViews: View
) {
    private var callback: Callback? = null

    private var onEndAnimate: ((showKeyboard: Boolean) -> Unit)? = null

    var isKeyboardShowed = false

    private var isPendingAnimation = false

    private var lastWindowInsets: WindowInsetsCompat? = null

    init {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val pendingAnimationAction = { isPendingAnimation || (!isKeyboardShowed && subFeatureContent.translationY == 0f) }

        val deferringInsetsListener = RootViewDeferringInsetsCallback(
            persistentInsetTypes = WindowInsetsCompat.Type.systemBars(),
            deferredInsetTypes = WindowInsetsCompat.Type.ime(),
            isPendingAnimation = pendingAnimationAction,
            callBack = object : Callback {
                override fun onInsetsChanged(insets: WindowInsetsCompat) {
                    lastWindowInsets = insets
                }

                override fun onChangeKeyboardSize(keyboardHeight: Int) {
                    val showed = keyboardHeight > 0
                    if (isKeyboardShowed == showed) return

                    callback?.onChangeKeyboardSize(keyboardHeight)
                    if (needUpdateSubFeatureContentSizeByKeyboard && keyboardHeight != 0) {
                        subFeatureContent.updateLayoutParams { height = keyboardHeight }
                    }

                    if (!isSupportAnimation()) {
                        if (showed) {
                            subFeatureContent.translationY = 0f
                        } else if (!pendingAnimationAction()) {
                            subFeatureContent.translationY = subFeatureContent.height.toFloat()
                        }
                        isKeyboardShowed = showed
                        rootView.post {
                            // reset pending
                            isPendingAnimation = false
                        }
                    }
                }

                override fun onEndAnimate(showKeyboard: Boolean) {
                    callback?.onEndAnimate(showKeyboard)
                    onEndAnimate?.also {
                        it(showKeyboard)
                        onEndAnimate = null
                    }

                    isKeyboardShowed = showKeyboard
                    if (showKeyboard) {
                        subFeatureContent.translationY = 0f
                    }

                    // reset pending
                    isPendingAnimation = false
                }

                override fun onStartAnimate(pendingAnimation: Boolean, showKeyboard: Boolean) {
                    if (!pendingAnimation && !showKeyboard) {
                        subFeatureContent.translationY = subFeatureContent.height.toFloat()
                    }
                    callback?.onStartAnimate(pendingAnimation, showKeyboard)
                }
            },
        )

        ViewCompat.setOnApplyWindowInsetsListener(rootView, deferringInsetsListener)

        if (isSupportAnimation()) {
            ViewCompat.setWindowInsetsAnimationCallback(rootView, deferringInsetsListener)
            animateViews.forEach {
                ViewCompat.setWindowInsetsAnimationCallback(
                    it,
                    TranslateDeferringInsetsAnimationCallback(
                        view = it,
                        persistentInsetTypes = WindowInsetsCompat.Type.systemBars(),
                        deferredInsetTypes = WindowInsetsCompat.Type.ime(),
                        isPendingAnimation = pendingAnimationAction,
                        dispatchMode = WindowInsetsAnimationCompat.Callback.DISPATCH_MODE_CONTINUE_ON_SUBTREE,
                    )
                )
            }
        }
    }

    fun getLastWindowInsets() = lastWindowInsets

    fun setCallback(callback: Callback?): AnimateKeyboardHelper {
        this.callback = callback
        return this
    }

    fun showKeyboard(target: View?, pendingAnimation: Boolean = false) {
        if (target == null) return
        this.isPendingAnimation = pendingAnimation
        target.requestFocus()
        KeyboardHelper.showKeyboard(target)
    }

    fun hideKeyboard(pendingAnimation: Boolean) {
        this.isPendingAnimation = pendingAnimation
        KeyboardHelper.hideKeyboard(activity)
    }

    interface Callback {
        fun onInsetsChanged(insets: WindowInsetsCompat) {}
        fun onChangeKeyboardSize(keyboardHeight: Int) {}
        fun onStartAnimate(pendingAnimation: Boolean, showKeyboard: Boolean) {}
        fun onEndAnimate(showKeyboard: Boolean) {}
    }
}

internal class RootViewDeferringInsetsCallback(
    val persistentInsetTypes: Int,
    val deferredInsetTypes: Int,
    val isPendingAnimation: () -> Boolean,
    val callBack: AnimateKeyboardHelper.Callback?,
) : WindowInsetsAnimationCompat.Callback(DISPATCH_MODE_CONTINUE_ON_SUBTREE),
    OnApplyWindowInsetsListener {
    init {
        require(persistentInsetTypes and deferredInsetTypes == 0) {
            "persistentInsetTypes and deferredInsetTypes can not contain any of " +
                    " same WindowInsetsCompat.Type values"
        }
    }

    private var view: View? = null
    private var lastWindowInsets: WindowInsetsCompat? = null

    private var deferredInsets = false
    private var targetHeight = 0

    override fun onApplyWindowInsets(
        v: View,
        windowInsets: WindowInsetsCompat
    ): WindowInsetsCompat {
        // Store the view and insets for us in onEnd() below
        view = v
        lastWindowInsets = windowInsets
        callBack?.onInsetsChanged(windowInsets)

        val types = when {
            // When the deferred flag is enabled, we only use the systemBars() insets
            deferredInsets -> persistentInsetTypes
            // Otherwise we handle the combination of the the systemBars() and ime() insets
            else -> persistentInsetTypes or deferredInsetTypes
        }

        // Finally we apply the resolved insets by setting them as padding
        val typeInsets = windowInsets.getInsets(types)
        Log.e("OnKeyboard: $typeInsets")
        if (!isPendingAnimation()) {
            v.setPadding(typeInsets.left, typeInsets.top, typeInsets.right, typeInsets.bottom)
        }
        windowInsets.getInsets(WindowInsetsCompat.Type.ime()).run {
            targetHeight = bottom
            callBack?.onChangeKeyboardSize(bottom)
        }

        // We return the new WindowInsetsCompat.CONSUMED to stop the insets being dispatched any
        // further into the view hierarchy. This replaces the deprecated
        // WindowInsetsCompat.consumeSystemWindowInsets() and related functions.
        return if (isSupportAnimation()) WindowInsetsCompat.CONSUMED else  windowInsets
    }

    override fun onPrepare(animation: WindowInsetsAnimationCompat) {
        if (animation.typeMask and deferredInsetTypes != 0) {
            // We defer the WindowInsetsCompat.Type.ime() insets if the IME is currently not visible.
            // This results in only the WindowInsetsCompat.Type.systemBars() being applied, allowing
            // the scrolling view to remain at it's larger size.
            deferredInsets = true
        }
    }

    override fun onStart(
        animation: WindowInsetsAnimationCompat,
        bounds: WindowInsetsAnimationCompat.BoundsCompat
    ): WindowInsetsAnimationCompat.BoundsCompat {
        callBack?.onStartAnimate(isPendingAnimation(), targetHeight > 0)
        return super.onStart(animation, bounds)
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnims: List<WindowInsetsAnimationCompat>
    ): WindowInsetsCompat {
        // This is a no-op. We don't actually want to handle any WindowInsetsAnimations
        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        if (deferredInsets && (animation.typeMask and deferredInsetTypes) != 0) {
            // If we deferred the IME insets and an IME animation has finished, we need to reset
            // the flag
            deferredInsets = false

            // And finally dispatch the deferred insets to the view now.
            // Ideally we would just call view.requestApplyInsets() and let the normal dispatch
            // cycle happen, but this happens too late resulting in a visual flicker.// Instead we manually dispatch the most recent WindowInsets to the view.
            if (lastWindowInsets != null && view != null) {
                ViewCompat.dispatchApplyWindowInsets(view!!, lastWindowInsets!!)
            }

            view?.post { callBack?.onEndAnimate(targetHeight > 0) }
        }
    }
}

internal class TranslateDeferringInsetsAnimationCallback(
    private val view: View,
    val persistentInsetTypes: Int,
    val deferredInsetTypes: Int,
    val isPendingAnimation: () -> Boolean,
    dispatchMode: Int = DISPATCH_MODE_STOP
) : WindowInsetsAnimationCompat.Callback(dispatchMode) {
    init {
        require(persistentInsetTypes and deferredInsetTypes == 0) {
            "persistentInsetTypes and deferredInsetTypes can not contain any of " +
                    " same WindowInsetsCompat.Type values"
        }
    }

    override fun onProgress(
        insets: WindowInsetsCompat,
        runningAnimations: List<WindowInsetsAnimationCompat>
    ): WindowInsetsCompat {
        // onProgress() is called when any of the running animations progress...

        // First we get the insets which are potentially deferred
        val typesInset = insets.getInsets(deferredInsetTypes)
        // Then we get the persistent inset types which are applied as padding during layout
        val otherInset = insets.getInsets(persistentInsetTypes)

        // Now that we subtract the two insets, to calculate the difference. We also coerce
        // the insets to be >= 0, to make sure we don't use negative insets.
        val diff = Insets.subtract(typesInset, otherInset).let {
            Insets.max(it, Insets.NONE)
        }

        // The resulting `diff` insets contain the values for us to apply as a translation
        // to the view
        if (!isPendingAnimation()) {
            view.translationX = (diff.left - diff.right).toFloat()
            view.translationY = (diff.top - diff.bottom).toFloat()
        }

        return insets
    }

    override fun onEnd(animation: WindowInsetsAnimationCompat) {
        // Once the animation has ended, reset the translation values
        if (!isPendingAnimation()) {
            view.translationX = 0f
            view.translationY = 0f
        }
    }
}
