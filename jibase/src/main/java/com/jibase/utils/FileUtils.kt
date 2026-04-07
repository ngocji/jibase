package com.jibase.utils

import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import java.io.*

const val prefixAsset = "file:///android_asset/"

fun copyFile(sourceFile: File, destFile: File): Boolean {
    try {
        if (!destFile.exists()) destFile.createNewFile()
        if (sourceFile.exists() && destFile.exists()) {
            sourceFile.inputStream().use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            return true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return false
}

fun copyFile(sourceStream: InputStream, destFile: File): Boolean {
    return try {
        if (!destFile.exists()) destFile.createNewFile()
        sourceStream.use { input ->
            destFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun copyFile(file: File, fileOutputStream: OutputStream) {
    try {
        val sourceStream: InputStream = FileInputStream(file)
        sourceStream.use { input ->
            fileOutputStream.use { output ->
                input.copyTo(output)
            }
        }
    } catch (e: java.lang.Exception) {
        e.printStackTrace()
    }
}


fun writeToFile(fromData: String, toFile: File): Boolean {
    return try {
        toFile.parentFile?.mkdirs()
        toFile.bufferedWriter().use { it.write(fromData) }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun writeToFile(fromStream: InputStream?, toFile: File): Boolean {
    return try {
        toFile.parentFile?.mkdirs()
        fromStream?.use { toFile.outputStream().write(it.readBytes()) }
        true
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

fun readTextFromInputStream(inputStream: InputStream): String {
    return inputStream.use {
        it.bufferedReader().readText()
    }
}

fun readTextFromAsset(context: Context, path: String): String {
    try {
        return readTextFromInputStream(context.assets.open(path))
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return "{\"data\":[]}"
}

fun getStreamFromFile(context: Context, path: String): InputStream? {
    return try {
        when {
            isAssetFilePath(path) -> context.assets.open(path.replace(prefixAsset, ""))
            File(path).exists() -> File(path).inputStream()
            else -> try {
                context.contentResolver.openInputStream(Uri.parse(path))
            } catch (ignored: FileNotFoundException) {
                // Fallback to open assets if file unable to open with default inputStream
                context.assets.open(path)
            }
        }
    } catch (throwable: Throwable) {
        throw Exception("Failed to get stream $path", throwable)
    }
}

fun isAssetFilePath(path: String): Boolean {
    return path.contains(prefixAsset)
}

fun getMimeType(f: File) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(f.extension)

fun getMimeType(ext: String?) = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.orEmpty())

fun File.isValidData() = exists() && canRead() && length() > 0