@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jibase.extensions

import androidx.lifecycle.ViewModel
import com.jibase.flow.ResultWrapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf

fun <T> ViewModel.resultApiCall(action: suspend () -> ResultWrapper<T>): Flow<ResultWrapper<T>> {
    return flow {
        emit(ResultWrapper.Loading)
        emit(action.invoke())
    }.catch { emit(ResultWrapper.Error(it)) }
}

fun <T> ViewModel.resultFlow(flowCreation: () -> Flow<ResultWrapper<T>>): Flow<ResultWrapper<T>> {
    return flowOf(ResultWrapper.Loading)
        .flatMapLatest { flowCreation() }
}