package com.jibase.ui.mvvm

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import com.jibase.extensions.destroy
import com.jibase.extensions.initBinding

abstract class BindFragment<VM : BindViewModel>(
        @LayoutRes private val layoutResId: Int,
        private val clazzViewModel: Class<VM>) : Fragment() {
    lateinit var viewModel: VM
    lateinit var binding: ViewDataBinding

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // create viewModel
        viewModel = ViewModelProviders.of(this).get(clazzViewModel).apply {
            onCreate(arguments)
        }
        // create data binding
        binding = initBinding(layoutResId, inflater, container, viewModel)

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