package com.jibase.permission

import android.os.Build
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.jibase.utils.Log

class PermissionsHelper<T> private constructor(val target: T) {
    companion object {
        // region set target
        @JvmStatic
        fun with(target: Fragment): PermissionsHelper<Fragment> {
            return PermissionsHelper(target)
        }

        @JvmStatic
        fun with(target: FragmentActivity): PermissionsHelper<FragmentActivity> {
            return PermissionsHelper(target)
        }
    }

    private var permissionFragment: PermissionFragment? = null
    private val permissionsRequests = mutableListOf<String>()

    private var onGrant: ((List<Permission>) -> Unit)? = null
    private var onDeny: ((List<Permission>) -> Unit)? = null
    private var onRevoke: ((List<Permission>) -> Unit)? = null

    init {
        getLazyPermissionFragment()
    }

    // region main
    fun request(vararg permissions: String): PermissionsHelper<T> {
        invalidRequestPermission(permissions)
        permissionsRequests.addAll(permissions)
        return this
    }

    fun onGrant(onGrant: (List<Permission>) -> Unit): PermissionsHelper<T> {
        this.onGrant = onGrant
        return this
    }


    fun onDeny(onDeny: (List<Permission>) -> Unit): PermissionsHelper<T> {
        this.onDeny = onDeny
        return this
    }

    fun onRevoke(onRevoke: (List<Permission>) -> Unit): PermissionsHelper<T> {
        this.onRevoke = onRevoke
        return this
    }

    fun onGrant(runnable: Runnable): PermissionsHelper<T> {
        return onGrant {
            runnable.run()
        }
    }

    fun onDeny(onDenyPermissionListener: OnDenyPermissionListener?): PermissionsHelper<T> {
        return onDeny { onDenyPermissionListener?.onDeny(it) }
    }


    fun onRevoke(onRevokePermissionListener: OnRevokePermissionListener?): PermissionsHelper<T> {
        return onRevoke { onRevokePermissionListener?.onRevoke(it) }
    }

    fun execute() {
        val allPermission = mutableListOf<Permission>()
        val denyPermission = mutableListOf<Permission>()
        val revokePermission = mutableListOf<Permission>()
        val unRequestPermission = mutableListOf<String>()

        permissionsRequests.forEach { per ->
            when {
                isGranted(per) -> {
                    allPermission.add(
                        Permission(
                            per,
                            granted = true,
                            shouldShowRequestPermissionRationale = true
                        )
                    )
                }

                isRevoked(per) -> {
                    val permission = Permission(
                        per, granted = false,
                        shouldShowRequestPermissionRationale = false
                    )

                    revokePermission.add(permission)
                    allPermission.add(permission)
                }

                else -> unRequestPermission.add(per)
            }
        }

        fun resultPermissions() {
            releasePermissionFragment()
            Log.d("Result: ${allPermission.joinToString { it.name }}" +
                    "\nDenyPermission: ${denyPermission.joinToString { it.name }}" +
                    "\nRevokePermission: ${revokePermission.joinToString { it.name }}" +
                    "\nUnRequestPermission: $unRequestPermission" +
                    "\nOnGrant=${onGrant}, onDeny=${onDeny}, onRevoke=${onRevoke}")
            when {
                allPermission.all { it.granted } -> onGrant?.invoke(allPermission)
                denyPermission.isNotEmpty() -> onDeny?.invoke(denyPermission)
                revokePermission.isNotEmpty() -> onRevoke?.invoke(revokePermission)
            }
        }

        Log.d("Per: ${getPermissionFragment()}" +
                "\nAllPermission: ${allPermission.joinToString { it.name }}" +
                "\nDenyPermission: ${denyPermission.joinToString { it.name }}" +
                "\nRevokePermission: ${revokePermission.joinToString { it.name }}" +
                "\nUnRequestPermission: $unRequestPermission")
        if (unRequestPermission.isNotEmpty()) {
            getPermissionFragment().requests(unRequestPermission) { resultMap ->
                Log.d("resultMap: ${resultMap.keys.joinToString { "${it}: ${resultMap[it]}" }}")
                resultMap.forEach { entry ->
                    val per = entry.key
                    val granted = entry.value
                    val shouldShowRequestPermissionRationale =
                        shouldShowRequestPermissionRationale(
                            getPermissionFragment().requireActivity(),
                            per
                        )
                    val permission = Permission(
                        per,
                        granted = granted,
                        shouldShowRequestPermissionRationale = shouldShowRequestPermissionRationale
                    )
                    allPermission.add(permission)

                    when {
                        isRevoked(per) -> revokePermission.add(permission)
                        !isGranted(per) -> denyPermission.add(permission)
                    }
                }

                resultPermissions()
            }
        } else {
            resultPermissions()
        }
    }

    /**
     * Returns true if the permission is already granted.
     *
     *
     * Always true if SDK &lt; 23.
     */
    fun isGranted(vararg permissions: String): Boolean {
        return !isRuntimeRequestPermission() || getPermissionFragment().isGranted(*permissions)
    }

    /**
     * Returns true if the permission has been revoked by a policy.
     *
     *
     * Always false if SDK &lt; 23.
     */
    fun isRevoked(vararg permissions: String): Boolean {
        return isRuntimeRequestPermission() && getPermissionFragment().isRevoked(*permissions)
    }

    fun isRuntimeRequestPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }

    // endregion

    // region init / release permission fragment
    private fun getLazyPermissionFragment() {
        val tag = PermissionFragment::class.java.name
        val fragmentManager = when (target) {
            is FragmentActivity -> target.supportFragmentManager
            is Fragment -> target.childFragmentManager
            else -> throw NullPointerException("Target must not be null")
        }
        permissionFragment = fragmentManager.findFragmentByTag(tag) as? PermissionFragment
            ?: // create newInstance
                    PermissionFragment().apply {
                        runCatching {
                            // add to manager
                            fragmentManager.beginTransaction()
                                .add(this, tag)
                                .commitNowAllowingStateLoss()
                        }
                    }
    }

    private fun releasePermissionFragment() {
        val tag = PermissionFragment::class.java.name
        val fragmentManager = when (target) {
            is FragmentActivity -> target.supportFragmentManager
            is Fragment -> target.childFragmentManager
            else -> throw NullPointerException("Target must not be null")
        }
        kotlin.runCatching {
            fragmentManager.findFragmentByTag(tag)?.let {
                fragmentManager.beginTransaction()
                    .remove(it)
                    .commitNowAllowingStateLoss()
            }
        }
            .onFailure {
                Log.d("Error release permission fragment")
            }
    }
    // endregion

    // region private method
    private fun invalidRequestPermission(permissions: Array<out String>) {
        if (permissions.isEmpty()) throw IllegalArgumentException("RxPermissions.request/requestEach requires at least one input permission")
    }

    private fun getPermissionFragment(): PermissionFragment {
        return permissionFragment
            ?: throw IllegalStateException("Permission is not implementation or not attach a context")
    }
    // endregion
}