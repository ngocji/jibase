package com.jibase.flow.cache

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
class DataCacheManager(private val scope: CoroutineScope) {
    private val dataCache = mutableMapOf<String, CacheModel<*, *>>()

    fun <T, P> getData(
        cacheKey: String,
        params: P,
        initialValue: T,
        context: CoroutineDispatcher = Dispatchers.IO,
        forceRefresh: Boolean = false,
        dataSource: (P) -> Flow<T>
    ): StateFlow<T> {
        val existing = dataCache[cacheKey] as? CacheModel<T, P>
        if (!forceRefresh && existing != null && params == existing.params) {
            return existing.stateFlow
        }

        val newStateFlow = existing?.stateFlow ?: MutableStateFlow(initialValue)
        val newCacheModel = CacheModel(params, dataSource, newStateFlow)
        dataCache[cacheKey] = newCacheModel

        scope.launch(context) {
            dataSource(params)
                .collect { value ->
                    newStateFlow.value = value
                }
        }
        return newStateFlow
    }

    fun clearCache(cacheKey: String) {
        dataCache.remove(cacheKey)
    }

    fun clearAllCache() {
        dataCache.clear()
    }
}