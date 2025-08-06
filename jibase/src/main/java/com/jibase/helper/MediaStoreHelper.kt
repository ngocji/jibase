@file:Suppress("MemberVisibilityCanBePrivate")

package com.jibase.helper

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Environment.getExternalStorageDirectory
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.jibase.permission.Permission
import com.jibase.permission.PermissionsHelper
import com.jibase.utils.Log
import com.jibase.utils.copyFile
import com.jibase.utils.getMimeType
import java.io.File


@Suppress("DEPRECATION")
object MediaStoreHelper {
    fun needRequestStoragePermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.S

    fun insert(data: Data): Uri? {
        return try {
            val resultUri: Uri?
            val values = ContentValues()
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, data.name)
            values.put(
                MediaStore.MediaColumns.MIME_TYPE,
                data.mimeType.ifBlank { getMimeType(data.file) }
            )
            values.put(MediaStore.MediaColumns.TITLE, data.name)

            resultUri = if (isUserRelativePath()) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, data.relativeSaveFolder)
                data.context.contentResolver.insert(
                    getContentUri(data.file, data.mimeType),
                    values
                )
            } else {
                if (!data.copyToNewPath && data.fileToExportBeforeAndroidQ != null) {
                    values.put(
                        MediaStore.MediaColumns.DATA,
                        data.fileToExportBeforeAndroidQ?.absolutePath
                    )
                    copyFile(data.file, data.fileToExportBeforeAndroidQ ?: return null)
                }
                data.context.contentResolver.insert(
                    getContentUri(data.file, data.mimeType),
                    values
                )
            }
            if (resultUri == null) {
                throw NullPointerException("error")
            }

            if (data.copyToNewPath) {
                val outputStream = data.context.contentResolver.openOutputStream(resultUri)
                    ?: throw NullPointerException("Error insert media")
                copyFile(data.file, outputStream)
            }

            if (data.deleteFileAfterCopy) {
                data.file.delete()
            }
            resultUri
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    @ChecksSdkIntAtLeast(api = Build.VERSION_CODES.Q)
    fun isUserRelativePath() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    private fun getContentUri(file: File, mimeType: String): Uri {
        val typeCheck = mimeType.takeIf { it.isNotBlank() } ?: getMimeType(file).orEmpty()
        return when {
            typeCheck.startsWith("video") -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            typeCheck.startsWith("image") -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            typeCheck.startsWith("audio") -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            else -> MediaStore.Files.getContentUri("external")
        }
    }

    data class Data(
        var context: Context,
        var name: String,
        var file: File,
        var mimeType: String = "",
        var relativeSaveFolder: String = "",
        var copyToNewPath: Boolean = true,
        var deleteFileAfterCopy: Boolean = false,

        var fileToExportBeforeAndroidQ: File? = null // use when !relativeSaveFolder and !copyToNewPath
    )


    fun deleteMedia(context: Context, uri: Uri): Boolean {
        return try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                val projection = arrayOf(MediaStore.MediaColumns.DATA)
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val filePath = cursor.getString(0)
                        File(filePath).delete()
                    }
                }
            }
            val result = context.contentResolver.delete(uri, null, null)
            Log.e("deleteMedia: $result")
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun createDesiredFileBeforeQ(toRelativePath: String): File {
        var desiredFile =
            File(getExternalStorageDirectory(), toRelativePath)
        val dir = desiredFile.parentFile
        val extensionWithDot =
            if (desiredFile.extension.isBlank()) "" else ".${desiredFile.extension}"
        val nameWithoutExtension = desiredFile.nameWithoutExtension
        var count = 0

        dir?.mkdirs()
        while (desiredFile.exists()) {
            count++
            desiredFile = File(dir, "$nameWithoutExtension ($count)$extensionWithDot")
        }
        desiredFile.createNewFile()
        return desiredFile
    }


    @Deprecated("")
    fun pickGallery(target: Fragment, requestCode: Int, onDeny: (List<Permission>) -> Unit) {
        requestStoragePermission(target, {
            try {
                val intent = obtainImageIntent()
                target.startActivityForResult(intent, requestCode)
            } catch (_: ActivityNotFoundException) {

            }
        }, onDeny)
    }


    @Deprecated("")
    fun pickGallery(
        target: FragmentActivity,
        requestCode: Int,
        onDeny: (List<Permission>) -> Unit
    ) {
        requestStoragePermission(target, {
            try {
                val intent = obtainImageIntent()
                target.startActivityForResult(intent, requestCode)
            } catch (_: ActivityNotFoundException) {

            }
        }, onDeny)
    }


    @Deprecated("")
    fun pickFile(
        target: Fragment,
        requestCode: Int,
        mimeType: String,
        onDeny: (List<Permission>) -> Unit
    ) {
        requestStoragePermission(target, {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = mimeType
            val chooser = Intent.createChooser(intent, "Select file")
            target.startActivityForResult(chooser, requestCode)
        }, onDeny)
    }

    @Deprecated("")
    fun pickFile(
        target: FragmentActivity,
        requestCode: Int,
        mimeType: String,
        onDeny: (List<Permission>) -> Unit
    ) {
        requestStoragePermission(target, {
            val intent = Intent(Intent.ACTION_GET_CONTENT)
            intent.type = mimeType
            val chooser = Intent.createChooser(intent, "Select file")
            target.startActivityForResult(chooser, requestCode)
        }, onDeny)
    }

    fun pickImage(
        target: FragmentActivity,
        launcher: ActivityResultLauncher<String>,
        onDeny: (List<Permission>) -> Unit
    ) {
        requestStoragePermission(target, {
            try {
                launcher.launch("image/*")
            } catch (_: ActivityNotFoundException) {

            }
        }, onDeny)
    }

    fun pickFile(
        target: FragmentActivity,
        launcher: ActivityResultLauncher<String>,
        mimeType: String,
        onDeny: (List<Permission>) -> Unit
    ) {
        requestStoragePermission(target, {
            try {
                launcher.launch(mimeType)
            } catch (_: Exception) {
            }
        }, onDeny)
    }

    fun pickCamera(
        target: FragmentActivity,
        launcher: ActivityResultLauncher<Intent>,
        uri: Uri? = null,
        onCreatedUri: (Uri?) -> Unit = {},
        onDeny: (List<Permission>) -> Unit = {}
    ) {
        requestStoragePermission(target, onGrant = {
            val targetUri = uri ?: newCameraUri(target)

            targetUri?.also {
                launcher.launch(
                    Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, it)
                    }
                )
            }

            onCreatedUri(targetUri)
        }, onDeny = {
            onDeny(it)
        })
    }

    private fun newCameraUri(context: Context): Uri? {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
            "Cam_${System.currentTimeMillis()}.png"
        )

        file.parentFile?.mkdirs()
        file.createNewFile()

        return insert(
            Data(
                context = context,
                name = file.name,
                file = file,
                mimeType = "image/png",
                relativeSaveFolder = Environment.DIRECTORY_PICTURES,
                deleteFileAfterCopy = true
            )
        )
    }

    private fun requestStoragePermission(
        target: Fragment,
        onGrant: () -> Unit,
        onDeny: (List<Permission>) -> Unit
    ) {
        if (needRequestStoragePermission()) {
            PermissionsHelper.with(target)
                .request(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                .onGrant(onGrant)
                .onDeny(onDeny)
                .execute()
        } else {
            onGrant()
        }
    }

    private fun requestStoragePermission(
        target: FragmentActivity,
        onGrant: () -> Unit,
        onDeny: (List<Permission>) -> Unit
    ) {
        if (needRequestStoragePermission()) {
            PermissionsHelper.with(target)
                .request(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
                .onGrant(onGrant)
                .onDeny(onDeny)
                .execute()
        } else {
            onGrant()
        }
    }

    private fun obtainImageIntent(): Intent = Intent.createChooser(
        Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "image/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        }, "Pick files: "
    )

    fun rename(context: Context, uri: Uri, newName: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, newName)
            }

            context.contentResolver.update(uri, values, null, null)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun requestDelete(
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        uris: List<Uri>
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = MediaStore.createDeleteRequest(
                context.contentResolver,
                uris
            )
            val intentSenderRequest = IntentSenderRequest.Builder(intent.intentSender).build()
            launcher.launch(intentSenderRequest)
            false
        } else {
            true
        }
    }

    fun requestWrite(
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        uris: List<Uri>
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = MediaStore.createWriteRequest(
                context.contentResolver,
                uris
            )
            val intentSenderRequest = IntentSenderRequest.Builder(intent.intentSender).build()
            launcher.launch(intentSenderRequest)
            false
        } else {
            true
        }
    }
}