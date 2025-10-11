package com.jibase.pref

import android.content.Context
import android.content.SharedPreferences
import com.jibase.helper.GsonManager
import com.jibase.utils.Log
import java.lang.reflect.Type

@Suppress("SpellCheckingInspection")
class SharePref(context: Context, prefName: String) : SharedPreferences.OnSharedPreferenceChangeListener {
    private val pref: SharedPreferences by lazy {
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
    }

    private var isRegistedChange = false

    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()

    override fun onSharedPreferenceChanged(
        sharedPreferences: SharedPreferences?,
        key: String?
    ) {
        synchronized(this) {
            listeners.forEach {
                it.onSharedPreferenceChanged(sharedPreferences, key)
            }
        }
    }

    fun registerChange(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) {
            if (!isRegistedChange) {
                pref.registerOnSharedPreferenceChangeListener(this)
                isRegistedChange = true
            }

            listeners.add(listener)
        }
    }

    fun unregisterChange(sharedPreferences: SharedPreferences.OnSharedPreferenceChangeListener) {
        synchronized(this) {
            listeners.remove(sharedPreferences)
            if (listeners.isEmpty()) {
                pref.unregisterOnSharedPreferenceChangeListener(this)
                isRegistedChange = false
            }
        }
    }

    fun getBoolean(key: String, defaultValue: Boolean) =
        pref.getString(key, defaultValue.toString())?.toBooleanStrictOrNull() ?: defaultValue

    fun getLong(key: String, defaultValue: Long) =
        pref.getString(key, defaultValue.toString())?.toLongOrNull() ?: defaultValue

    fun getFloat(key: String, defaultValue: Float) =
        pref.getString(key, defaultValue.toString())?.toFloatOrNull() ?: defaultValue

    fun getDouble(key: String, defaultValue: Double) =
        pref.getString(key, defaultValue.toString())?.toDoubleOrNull() ?: defaultValue

    fun getString(key: String, defaultValue: String) =
        pref.getString(key, defaultValue) ?: defaultValue

    fun getInt(key: String, defaultValue: Int) =
        pref.getString(key, defaultValue.toString())?.toIntOrNull() ?: defaultValue

    fun <T> getObject(key: String, type: Type): T? {
        val content = getString(key, "")
        return try {
            if (content.isBlank()) {
                null
            } else {
                GsonManager.fromJson<T>(content, type)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun <T> getObject(key: String, clzz: Class<T>): T? {
        val content = getString(key, "")
        return try {
            if (content.isBlank()) {
                null
            } else {
                GsonManager.fromJson(content, clzz)
            }
        } catch (e: Exception) {
            null
        }
    }

    fun putBoolean(key: String, value: Boolean) {
        pref.edit().putString(key, value.toString()).apply()
    }

    fun putLong(key: String, value: Long) {
        pref.edit().putString(key, value.toString()).apply()
    }

    fun putFloat(key: String, value: Float) {
        pref.edit().putString(key, value.toString()).apply()
    }

    fun putDouble(key: String, value: Double) {
        pref.edit().putString(key, value.toString()).apply()
    }

    fun putString(key: String, value: String) {
        pref.edit().putString(key, value).apply()
    }

    fun putInt(key: String, value: Int) {
        pref.edit().putString(key, value.toString()).apply()
    }

    fun <T> putObject(key: String, value: T?) {
        if (value == null) {
            pref.edit().remove(key).apply()
        } else {
            putString(key, GsonManager.toJson(value))
        }
    }

    // end region

    fun contains(key: String): Boolean {
       return pref.contains(key)
    }

    fun remove(vararg keys: String) {
        pref.edit().apply {
            keys.forEach { remove(it) }
        }.apply()
    }

    fun <T> put(key: String, value: T?) {
        with(pref.edit()) {
            when (value) {
                is Boolean, is Float, is Int, is Long, is String, is Double -> {
                    Log.d("share: push normal data")
                    putString(key, value.toString())
                }

                else -> {
                    Log.d("share: push object data")
                    if (value == null) {
                        remove(key)
                    } else {
                        putString(key, GsonManager.toJson(value))
                    }
                }
            }

            apply()
        }
    }
}