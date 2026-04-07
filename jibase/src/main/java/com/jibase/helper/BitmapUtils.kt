package com.jibase.helper

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.NinePatchDrawable
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.annotation.WorkerThread
import androidx.exifinterface.media.ExifInterface
import com.jibase.utils.getStreamFromFile
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

object BitmapUtils {

    @WorkerThread
    fun saveBitmapToFile(file: File, bmp: Bitmap, compressFormat: Bitmap.CompressFormat): File {
        saveBitmapTo(FileOutputStream(file), bmp, compressFormat)
        return file
    }

    @WorkerThread
    fun saveBitmapTo(fos: OutputStream, bmp: Bitmap, compressFormat: Bitmap.CompressFormat) {
        runCatching {
            bmp.compress(compressFormat, 100, fos)
            fos.flush()
            fos.close()
        }
    }

    fun decodeBitmapByScreenSize(context: Context, path: String): Bitmap? {
        val display = context.resources.displayMetrics
        return decodeBitmap(context, path, display.widthPixels, display.heightPixels)
    }

    fun decodeAndScaleBitmap(
        context: Context,
        path: String,
        width: Int = -1,
        height: Int = -1
    ): Bitmap? {
        return when {
            width in -1..1 || height in -1..1 -> decodeBitmap(context, path, width, height)
            else -> {
                var bitmap = decodeBitmap(context, path, width - 1, height - 1)
                if (bitmap != null && (bitmap.width != width || bitmap.height != height)) {
                    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, width, height, true)
                    if (scaledBitmap != bitmap) {
                        bitmap.safeRecycle()
                        bitmap = scaledBitmap
                    }
                }
                return bitmap
            }
        }
    }

    fun decodeBitmap(
        context: Context,
        path: String,
        reqWidth: Int = -1,
        reqHeight: Int = -1
    ): Bitmap? {
        val orientation = getStreamFromFile(context, path)?.use { stream ->
            getOrientation(stream)
        } ?: ExifInterface.ORIENTATION_NORMAL

        // First decode with inJustDecodeBounds=true to check dimensions
        return BitmapFactory.Options().run {
            if (reqWidth != -1 && reqHeight != -1) {
                inJustDecodeBounds = true

                getStreamFromFile(context, path).use { stream ->
                    getBitmap(stream, this)
                }

                // Calculate inSampleSize
                inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight, orientation)

                // Decode bitmap with inSampleSize set
                inJustDecodeBounds = false
            }

            val bitmap = getStreamFromFile(context, path).use { stream -> getBitmap(stream, this) }

            val matrix = getRotatedMatrix(orientation)

            rotateBitmapIfNeed(matrix, bitmap)
        }
    }

    fun decodeBitmap(
        context: Context,
        @DrawableRes resource: Int,
        reqWidth: Int = -1,
        reqHeight: Int = -1
    ): Bitmap? {
        // First decode with inJustDecodeBounds=true to check dimensions
        return BitmapFactory.Options().run {
            if (reqWidth != -1 && reqHeight != -1) {
                inJustDecodeBounds = true

                getBitmap(context, resource, this)

                // Calculate inSampleSize
                inSampleSize = calculateInSampleSize(
                    this,
                    reqWidth,
                    reqHeight,
                    ExifInterface.ORIENTATION_NORMAL
                )

                // Decode bitmap with inSampleSize set
                inJustDecodeBounds = false
            }

            val bitmap = getBitmap(context, resource, this)

            val matrix = getRotatedMatrix(ExifInterface.ORIENTATION_NORMAL)

            rotateBitmapIfNeed(matrix, bitmap)
        }
    }

    @Suppress("unused")
    @Throws
    fun bitmapToArray(target: Bitmap?): ByteArray {
        val stream = ByteArrayOutputStream()
        target?.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    private fun getBitmap(
        inputStream: InputStream?,
        options: BitmapFactory.Options? = null
    ): Bitmap? {
        return try {
            if (inputStream == null) return null
            BitmapFactory.decodeStream(inputStream, null, options)
        } catch (e: OutOfMemoryError) {
            return null
        }
    }

    private fun getBitmap(
        context: Context,
        @DrawableRes resource: Int,
        options: BitmapFactory.Options? = null
    ): Bitmap? {
        return try {
            BitmapFactory.decodeResource(context.resources, resource, options)
        } catch (e: OutOfMemoryError) {
            return null
        }
    }

    private fun parseDimensionFromOption(
        options: BitmapFactory.Options,
        orientation: Int
    ): Pair<Int, Int> {
        return options.run {
            when (orientation) {
                ExifInterface.ORIENTATION_TRANSPOSE,
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_ROTATE_270 -> outWidth to outHeight

                else -> outHeight to outWidth
            }
        }
    }

    private fun calculateInSampleSize(
        options: BitmapFactory.Options,
        reqWidth: Int,
        reqHeight: Int,
        orientation: Int
    ): Int {
        // Raw height and width of image
        val (height: Int, width: Int) = parseDimensionFromOption(options, orientation)
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {

            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            // Calculate the largest inSampleSize value that is a power of 2 and keeps both
            // height and width larger than the requested height and width.
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }

        return inSampleSize
    }

    private fun rotateBitmapIfNeed(matrix: Matrix?, originalBitmap: Bitmap?): Bitmap? {
        if (matrix == null || originalBitmap == null) return originalBitmap

        try {
            val result = Bitmap.createBitmap(
                originalBitmap,
                0,
                0,
                originalBitmap.width,
                originalBitmap.height,
                matrix,
                true
            )
            originalBitmap.recycle()
            return result
        } catch (e: Exception) {
            e.printStackTrace()
        } catch (e: OutOfMemoryError) {
            e.printStackTrace()
        }

        return originalBitmap
    }

    private fun getRotatedMatrix(orientation: Int): Matrix? {
        val matrix = Matrix()

        when (orientation) {
            ExifInterface.ORIENTATION_NORMAL -> return null
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                matrix.setRotate(180f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.setRotate(90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.setRotate(-90f)
                matrix.postScale(-1f, 1f)
            }

            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
            else -> return null
        }
        return matrix
    }

    private fun getOrientation(inputStream: InputStream): Int {
        return ExifInterface(inputStream).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED
        )
    }
}

fun Bitmap?.safeRecycle() {
    if (this?.isRecycled == false) {
        this.recycle()
    }
}

fun Bitmap.trim(@ColorInt color: Int = Color.TRANSPARENT): Bitmap {
    var top = height
    var bottom = 0
    var right = width
    var left = 0

    var colored = IntArray(width) { color }
    var buffer = IntArray(width)

    for (y in bottom until top) {
        getPixels(buffer, 0, width, 0, y, width, 1)
        if (!colored.contentEquals(buffer)) {
            bottom = y
            break
        }
    }

    for (y in top - 1 downTo bottom) {
        getPixels(buffer, 0, width, 0, y, width, 1)
        if (!colored.contentEquals(buffer)) {
            top = y
            break
        }
    }

    val heightRemaining = top - bottom
    colored = IntArray(heightRemaining) { color }
    buffer = IntArray(heightRemaining)

    for (x in left until right) {
        getPixels(buffer, 0, 1, x, bottom, 1, heightRemaining)
        if (!colored.contentEquals(buffer)) {
            left = x
            break
        }
    }

    for (x in right - 1 downTo left) {
        getPixels(buffer, 0, 1, x, bottom, 1, heightRemaining)
        if (!colored.contentEquals(buffer)) {
            right = x
            break
        }
    }

    return Bitmap.createBitmap(this, left, bottom, right - left, top - bottom)
}

fun Bitmap.isTransparent(): Boolean {
    for (y in 0 until height) {
        for (x in 0 until width) {
            if (getPixel(x, y) != 0) {
                return false
            }
        }
    }

    return true
}

fun Bitmap.toNinePathDrawable(context: Context): Drawable? {
    if (ninePatchChunk?.isNotEmpty() != true) return null
    return NinePatchDrawable(context.resources, this, ninePatchChunk, Rect(), null)
}