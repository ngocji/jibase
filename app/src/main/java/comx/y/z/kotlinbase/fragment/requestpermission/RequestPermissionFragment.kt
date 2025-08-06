package comx.y.z.kotlinbase.fragment.requestpermission

import android.Manifest
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.jibase.extensions.viewBinding
import com.jibase.permission.OnDenyPermissionListener
import com.jibase.permission.Permission
import com.jibase.permission.PermissionsHelper
import com.jibase.pref.SharePref
import com.jibase.utils.Log
import comx.y.z.kotlinbase.R
import comx.y.z.kotlinbase.databinding.FragmentRequestPermissionBinding
import comx.y.z.kotlinbase.fragment.MainViewModel
import javax.inject.Inject

class RequestPermissionFragment : Fragment(R.layout.fragment_request_permission) {
    private val mainViewModel by viewModels<MainViewModel>()

    private val binding by viewBinding(FragmentRequestPermissionBinding::bind)

    @Inject
    lateinit var sharePref: SharePref
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btn.setOnClickListener {
            PermissionsHelper.with(this)
                .request(
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.CAMERA
                )
                .onGrant {
                    binding.btn.text = "Granted"
                }
                .onDeny(object : OnDenyPermissionListener {
                    override fun onDeny(permissions: List<Permission>) {
                        binding.btn.text = "Deny"
                    }
                })
                .execute()
        }
    }
}