package com.jibase.ui.mvvm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import com.jibase.anotation.Inflate
import com.jibase.anotation.InflateHelper
import com.jibase.extensions.destroy
import com.jibase.extensions.initBinding

@Suppress("LeakingThis", "UNCHECKED_CAST")
abstract class BindNoViewModelFragment: Fragment() {
    open val inflate: Inflate by lazy { InflateHelper.getAnnotation(this) }
    lateinit var binding: ViewDataBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // create data binding
        binding = initBinding(inflate.layout, inflater, container, null)

        // return the view
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        onViewReady(savedInstanceState)
        onViewListener()
    }

    abstract fun onViewReady(savedInstanceState: Bundle?)

    open fun onViewListener() {
        // free implement
    }

    override fun onDestroyView() {
        // Hacky : There's a memory leak issue with data binding if we don't set lifeCycleOwner to null
        binding.destroy()
        super.onDestroyView()
    }
}