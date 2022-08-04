package com.jibase.pref

import android.content.Context
import android.content.SharedPreferences
import com.jibase.helper.GsonManager
import com.jibase.utils.Log
import java.lang.reflect.Type

class SharePref(context: Context, prefName: String) {
    private val pref: SharedPreferences by lazy {
        context.getSharedPreferences(prefName, Context.MODE_PRIVATE)
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

    fun <T> getObject(key: String, type: Type, defaultValue: T?): T? {
        val content = getString(key, "")
        return try {
            if (content.isBlank()) {
                defaultValue
            } else {
                GsonManager.fromJson(content, type)
            }
        } catch (e: Exception) {
            defaultValue
        }
    }

    fun getObject(key: String, type: Type) = getObject(key, type, null)

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