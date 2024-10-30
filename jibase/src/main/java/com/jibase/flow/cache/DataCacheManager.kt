package com.jibase.flow.cache

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@Suppress("UNCHECKED_CAST")
class DataCacheManager {
    private val dataCache = mutableMapOf<String, CacheModel<*, *>>()

    fun <T, P> getData(
        scope: CoroutineScope,
        cacheKey: String,
        params: P,
        initialValue: T,
        dataSource: (P) -> Flow<T>
    ): StateFlow<T> {
        val cacheModel = dataCache[cacheKey] as? CacheModel<T, P>
        if (cacheModel != null) {
            return cacheModel.stateFlow
        }


        val newStateFlow = dataSource(params)
            .stateIn(
                scope = scope,
                started = SharingStarted.WhileSubscribed(5000),
                initialValue = initialValue
            )
        val newCacheModel = CacheModel(params, dataSource, newStateFlow)
        dataCache[cacheKey] = newCacheModel

        return newStateFlow
    }

    fun clearCache(cacheKey: String) {
        dataCache.remove(cacheKey)
    }

    fun clearAllCache() {
        dataCache.clear()
    }
}