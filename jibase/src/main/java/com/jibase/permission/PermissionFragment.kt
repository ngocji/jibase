package com.jibase.permission

import android.annotation.TargetApi
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.jibase.utils.Log

class PermissionFragment : Fragment() {
    private val launchMultiplePermission =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            Log.d("ResultPermission: $result -> ${resultAction}")
            resultAction?.invoke(result)
        }

    private var resultAction: ((Map<String, Boolean>) -> Unit)? = null

    @TargetApi(Build.VERSION_CODES.M)
    fun requests(permissions: List<String>, action: (Map<String, Boolean>) -> Unit) {
        resultAction = action
        launchMultiplePermission.launch(permissions.toTypedArray())
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun isGranted(vararg permissions: String): Boolean {
        val fragmentActivity = activity
            ?: throw IllegalStateException("This fragment must be attached to an activity.")
        return permissions.all {
            fragmentActivity.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
        }
    }

    @TargetApi(Build.VERSION_CODES.M)
    fun isRevoked(vararg permissions: String): Boolean {
        val fragmentActivity = activity
            ?: throw IllegalStateException("This fragment must be attached to an activity.")
        return permissions.all {
            fragmentActivity.packageManager.isPermissionRevokedByPolicy(
                it,
                fragmentActivity.packageName
            )
        }
    }
}