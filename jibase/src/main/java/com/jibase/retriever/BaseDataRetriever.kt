package com.jibase.retriever

import android.content.Context
import com.jibase.helper.AssetsUtils
import com.jibase.helper.GsonManager
import com.jibase.pref.DataStoreHelper
import com.jibase.utils.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.lang.reflect.Type

abstract class BaseDataRetriever<T>(
    private val prefLastRefreshTime: String,
    private val refreshInterval: Long,
    private val dataStoreHelper: DataStoreHelper
) {
    private var data: T? = null

    abstract suspend fun getRemote(): T?
    abstract suspend fun getLocal(): T?
    abstract suspend fun saveToLocal(data: T)

    suspend fun getCacheAsset(context: Context, data: String, type: Type) = withContext(Dispatchers.IO) {
        try {
            GsonManager.fromJson<T>(
                AssetsUtils.readTextFromAsset(
                    context = context,
                    path = data
                ), type
            )
        } catch (e: Exception) {
            Log.e("error read text from asset: $data --> $type")
            null
        }
    }

    suspend fun getData(): T? {
        return when (data) {
            null -> {
                when {
                    needRefresh() -> {
                        getRemote()?.apply { saveToLocal(this) } ?: getLocal()
                    }

                    else -> getLocal()
                }.apply {
                    data = this
                }
            }

            else -> data ?: throw NullPointerException("Empty data")
        }
    }

    fun isAvailableData() = data != null

    private suspend fun needRefresh(): Boolean {
        val lastTimeLoaded = dataStoreHelper.getLong(prefLastRefreshTime, 0L).first()
        val currentTime = System.currentTimeMillis()
        return currentTime - lastTimeLoaded > refreshInterval
    }

    private suspend fun setRefreshTime() {
        dataStoreHelper.put(prefLastRefreshTime, System.currentTimeMillis())
    }
}