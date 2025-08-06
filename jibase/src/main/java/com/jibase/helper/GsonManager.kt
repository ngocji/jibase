package com.jibase.helper

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import com.jibase.helper.GsonManager.DEFAULT
import com.jibase.utils.Log
import java.lang.reflect.Type

object GsonManager {
    const val DEFAULT = "DefaultGsonType"
    private val gsonMap by lazy { mutableMapOf<String, Gson>() }

    init {
        gsonMap[DEFAULT] = GsonBuilder()
            .serializeSpecialFloatingPointValues()
            .create()
    }

    @JvmStatic
    fun createGson(key: String, gson: Gson) {
        gsonMap[key] = gson
    }

    @JvmStatic
    inline fun <reified T> fromJson(data: String): T? = fromJson(data, getTypeToken<T>())

    @JvmStatic
    fun <T> fromJson(data: String, type: Type): T? = fromJson(data, type, DEFAULT)

    @JvmStatic
    fun <T> fromJson(data: String, type: Type, gsonType: String = DEFAULT) =
        try {
            gsonMap[gsonType]?.fromJson<T>(data, type)
        } catch (e: Exception) {
            Log.e("Error fromJson: $e")
            null
        }

    @JvmStatic
    inline fun <reified T> fromJson(data: String, gsonType: String): T? =
        fromJson(data, getTypeToken<T>(), gsonType)


    @JvmStatic
    fun <T> fromJson(data: String, classOfType: Class<T>) =
        fromJson(data, classOfType, DEFAULT)

    @JvmStatic
    fun <T> fromJson(data: String, classOfType: Class<T>, gsonType: String) = try {
        gsonMap[gsonType]?.fromJson(data, classOfType)
    } catch (e: Exception) {
        Log.e("Error fromJson: $e")
        null
    }

    @JvmStatic
    fun <T> toJson(data: T): String = toJson(data, DEFAULT)

    @JvmStatic
    fun <T> toJson(data: T, gsonType: String = DEFAULT): String {
        return try {
            gsonMap[gsonType]?.toJson(data) ?: ""
        } catch (e: Exception) {
            Log.e("Error toJson: $e")
            ""
        }
    }
}

inline fun <reified T> fromJson(
    data: String,
    type: Type = getTypeToken<T>(),
    gsonType: String = DEFAULT
): T? {
    return GsonManager.fromJson<T>(data, type, gsonType)
}

inline fun <reified T> fromJson(data: String, gsonType: String = DEFAULT): T? {
    return GsonManager.fromJson(data, T::class.java, gsonType = gsonType)
}

// get type token from list
inline fun <reified T> getTypeToken(): Type = object : TypeToken<T>() {}.type
