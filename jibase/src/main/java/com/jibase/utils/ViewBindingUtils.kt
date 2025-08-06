@file:Suppress("UNCHECKED_CAST")

package com.jibase.utils

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

object ViewBindingUtils {
    inline fun <reified T : ViewBinding> bind(view: View): T {
        return T::class.java
            .getMethod("bind", View::class.java)
            .invoke(null, view) as T
    }

    fun <T : ViewBinding> bind(view: View, clazz: Class<T>): T {
        return clazz
            .getMethod("bind", View::class.java)
            .invoke(null, view) as T
    }

    fun <T : ViewBinding> inflate(
        inflater: LayoutInflater,
        clazz: Class<T>
    ): T {
        return clazz
            .getMethod("inflate", LayoutInflater::class.java)
            .invoke(null, inflater) as T
    }

    fun <T : ViewBinding> inflate(
        inflater: LayoutInflater,
        root: ViewGroup?,
        attachToRoot: Boolean,
        clazz: Class<T>
    ): T {
        return clazz
            .getMethod(
                "inflate",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Boolean::class.java
            )
            .invoke(null, inflater, root, attachToRoot) as T
    }
}