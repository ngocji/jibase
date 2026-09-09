package com.jibase.pref

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.jibase.helper.GsonManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.lang.reflect.Type

/**
 * SharedPreferences-like wrapper backed by Jetpack DataStore (Preferences), exposing reads as [Flow].
 */
@Suppress("SpellCheckingInspection")
class DataStoreHelper(context: Context, prefName: String) {
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(prefName) }
        )
    }

    fun getBoolean(key: String, defaultValue: Boolean): Flow<Boolean> =
        dataStore.data.map { it[booleanPreferencesKey(key)] ?: defaultValue }

    fun getLong(key: String, defaultValue: Long): Flow<Long> =
        dataStore.data.map { it[longPreferencesKey(key)] ?: defaultValue }

    fun getFloat(key: String, defaultValue: Float): Flow<Float> =
        dataStore.data.map { it[floatPreferencesKey(key)] ?: defaultValue }

    fun getDouble(key: String, defaultValue: Double): Flow<Double> =
        dataStore.data.map { it[doublePreferencesKey(key)] ?: defaultValue }

    fun getString(key: String, defaultValue: String): Flow<String> =
        dataStore.data.map { it[stringPreferencesKey(key)] ?: defaultValue }

    fun getInt(key: String, defaultValue: Int): Flow<Int> =
        dataStore.data.map { it[intPreferencesKey(key)] ?: defaultValue }

    fun <T> getObject(key: String, type: Type): Flow<T?> =
        dataStore.data.map { pref ->
            val content = pref[stringPreferencesKey(key)]
            if (content.isNullOrBlank()) {
                null
            } else {
                try {
                    GsonManager.fromJson<T>(content, type)
                } catch (e: Exception) {
                    null
                }
            }
        }

    fun <T> getObject(key: String, clzz: Class<T>): Flow<T?> =
        dataStore.data.map { pref ->
            val content = pref[stringPreferencesKey(key)]
            if (content.isNullOrBlank()) {
                null
            } else {
                try {
                    GsonManager.fromJson(content, clzz)
                } catch (e: Exception) {
                    null
                }
            }
        }

    // Blocking variants — for callers that can't use a CoroutineScope
    fun getBooleanBlocking(key: String, defaultValue: Boolean): Boolean =
        runBlocking { getBoolean(key, defaultValue).first() }

    fun getLongBlocking(key: String, defaultValue: Long): Long =
        runBlocking { getLong(key, defaultValue).first() }

    fun getFloatBlocking(key: String, defaultValue: Float): Float =
        runBlocking { getFloat(key, defaultValue).first() }

    fun getDoubleBlocking(key: String, defaultValue: Double): Double =
        runBlocking { getDouble(key, defaultValue).first() }

    fun getStringBlocking(key: String, defaultValue: String): String =
        runBlocking { getString(key, defaultValue).first() }

    fun getIntBlocking(key: String, defaultValue: Int): Int =
        runBlocking { getInt(key, defaultValue).first() }

    fun <T> getObjectBlocking(key: String, type: Type): T? =
        runBlocking { getObject<T>(key, type).first() }

    fun <T> getObjectBlocking(key: String, clzz: Class<T>): T? =
        runBlocking { getObject(key, clzz).first() }

    fun containsBlocking(key: String): Boolean =
        runBlocking { contains(key).first() }

    suspend fun putBoolean(key: String, value: Boolean) {
        dataStore.edit { it[booleanPreferencesKey(key)] = value }
    }

    suspend fun putLong(key: String, value: Long) {
        dataStore.edit { it[longPreferencesKey(key)] = value }
    }

    suspend fun putFloat(key: String, value: Float) {
        dataStore.edit { it[floatPreferencesKey(key)] = value }
    }

    suspend fun putDouble(key: String, value: Double) {
        dataStore.edit { it[doublePreferencesKey(key)] = value }
    }

    suspend fun putString(key: String, value: String) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun putInt(key: String, value: Int) {
        dataStore.edit { it[intPreferencesKey(key)] = value }
    }

    suspend fun <T> putObject(key: String, value: T?) {
        if (value == null) {
            dataStore.edit { it.remove(stringPreferencesKey(key)) }
        } else {
            putString(key, GsonManager.toJson(value))
        }
    }

    // end region

    fun contains(key: String): Flow<Boolean> =
        dataStore.data.map { it.contains(stringPreferencesKey(key)) }

    suspend fun remove(vararg keys: String) {
        dataStore.edit { pref ->
            keys.forEach { pref.remove(stringPreferencesKey(it)) }
        }
    }

    suspend fun <T> put(key: String, value: T?) {
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Float -> putFloat(key, value)
            is Int -> putInt(key, value)
            is Long -> putLong(key, value)
            is String -> putString(key, value)
            is Double -> putDouble(key, value)
            else -> putObject(key, value)
        }
    }
}
