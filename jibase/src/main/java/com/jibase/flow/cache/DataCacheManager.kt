package com.jibase.flow.cache

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Suppress("UNCHECKED_CAST")
class DataCacheManager(private val scope: CoroutineScope) {
    private val dataCache = mutableMapOf<String, CacheModel<*, *>>()
    private val flowJobs = mutableMapOf<String, Job>()

    fun <T> getFlow(
        cacheKey: String,
        initialValue: T,
        context: CoroutineDispatcher = Dispatchers.IO,
        forceRefresh: Boolean = false,
        flowProvider: () -> Flow<T>
    ): Flow<T> {
        val existing = dataCache[cacheKey] as? CacheModel<T, *>
        if (!forceRefresh && existing != null) {
            return existing.stateFlow
        }

        val newStateFlow = existing?.stateFlow ?: MutableStateFlow(initialValue)
        val newCacheModel = CacheModel(null, { flowProvider() }, newStateFlow)
        dataCache[cacheKey] = newCacheModel

        // cancel prev
        flowJobs.remove(cacheKey)?.cancel()
        val job = scope.launch(context) {
            flowProvider()
                .collectLatest { newStateFlow.tryEmit(it) }
        }
        flowJobs[cacheKey] = job
        return newStateFlow
    }

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
        flowJobs.remove(cacheKey)?.cancel()
    }

    fun clearAllCache() {
        dataCache.clear()
        flowJobs.onEach { (_, job) -> job.cancel() }.clear()
    }
}