package com.jibase.flow.cache

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class CacheModel<T, P>(
    val params: P,
    val dataSource: (P) -> Flow<T>,
    val stateFlow: StateFlow<T>
)