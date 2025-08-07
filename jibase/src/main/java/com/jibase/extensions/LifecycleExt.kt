package com.jibase.extensions

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

fun <T> LifecycleOwner.collectAtLifecycle(
    state: Lifecycle.State = Lifecycle.State.STARTED,
    action: suspend CoroutineScope.() -> Unit
) {
    lifecycleScope.launch {
        repeatOnLifecycle(state, action)
    }
}

fun <T> FragmentActivity.collect(flow: Flow<T>, action: suspend (value: T) -> Unit) {
    collectAtLifecycle<T> {
        flow.collectLatest(action)
    }
}

fun <T> FragmentActivity.collectOne(
    flow: MutableStateFlow<T?>,
    action: suspend (value: T) -> Unit
) {
    collectAtLifecycle<T> {
        flow.collect {
            it ?: return@collect
            action.invoke(it)
            flow.tryEmit(null)
        }
    }
}

fun <T> FragmentActivity.collectNotNull(
    flow: Flow<T?>,
    action: suspend (value: T) -> Unit
) {
    collectAtLifecycle<T> {
        flow.collect {
            it ?: return@collect
            action.invoke(it)
        }
    }
}


fun <T> FragmentActivity.collectWhenResume(flow: Flow<T>, action: suspend (value: T) -> Unit) {
    collectAtLifecycle<T>(state = Lifecycle.State.RESUMED) {
        flow.collectLatest(action)
    }
}

fun <T> FragmentActivity.run(flow: Flow<T>) {
    collectAtLifecycle<T> {
        flow.collect {}
    }
}


fun <T> Fragment.collect(flow: Flow<T>, action: suspend (value: T) -> Unit) {
    viewLifecycleOwner.collectAtLifecycle<T> {
        flow.collectLatest(action)
    }
}

fun <T> Fragment.collectOne(flow: MutableStateFlow<T?>, action: suspend (value: T) -> Unit) {
    viewLifecycleOwner.collectAtLifecycle<T> {
        flow.collect {
            it ?: return@collect
            action.invoke(it)
            flow.tryEmit(null)
        }
    }
}

fun <T> Fragment.collectNotNull(flow: Flow<T?>, action: suspend (value: T) -> Unit) {
    viewLifecycleOwner.collectAtLifecycle<T> {
        flow.collect {
            it ?: return@collect
            action.invoke(it)
        }
    }
}


fun <T> Fragment.collectWhenResume(flow: Flow<T>, action: suspend (value: T) -> Unit) {
    viewLifecycleOwner.collectAtLifecycle<T> {
        flow.collectLatest(action)
    }
}

fun <T> Fragment.collect(channel: Channel<T>, action: suspend (value: T) -> Unit) {
    viewLifecycleOwner.collectAtLifecycle<T> {
        for (value in channel) {
            action.invoke(value)
        }
    }
}

fun <T> FragmentActivity.collect(channel: Channel<T>, action: suspend (value: T) -> Unit) {
    collectAtLifecycle<T> {
        for (value in channel) {
            action.invoke(value)
        }
    }
}

fun <T> Fragment.run(flow: Flow<T>) {
    viewLifecycleOwner.collectAtLifecycle<T> {
        flow.collect {}
    }
}
