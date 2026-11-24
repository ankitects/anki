// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.core.view.allViews
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.permissions.MANAGE_EXTERNAL_STORAGE
import com.ichi2.utils.Permissions.openAppSettingsScreenForPermission
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreenForPermission
import timber.log.Timber

/**
 * Base class for constructing a permissions screen
 *
 * @see PermissionsActivity
 */
abstract class PermissionsFragment(
    @LayoutRes contentLayoutId: Int,
) : Fragment(contentLayoutId) {
    /**
     * All the [PermissionsItem]s in the fragment.
     * Must be called ONLY AFTER [onCreateView]
     */
    val permissionsItems: List<PermissionsItem>
        by lazy { view?.allViews?.filterIsInstance<PermissionsItem>()?.toList() ?: emptyList() }

    protected fun hasAllPermissions() = permissionsItems.all { it.areGranted }

    override fun onResume() {
        super.onResume()
        permissionsItems.forEach { it.updateSwitchCheckedStatus() }
        setFragmentResult(
            REQUEST_PERMISSIONS,
            Bundle().apply { putBoolean(RESULT_HAS_ALL_PERMISSIONS, hasAllPermissions()) },
        )
    }

    /** Opens the Android 'MANAGE_ALL_FILES' page if the device provides this feature */
    @RequiresApi(Build.VERSION_CODES.R)
    protected fun ActivityResultLauncher<Intent>.showManageAllFilesScreen() {
        val intent =
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.fromParts("package", requireActivity().packageName, null),
            )

        // From the docs: [ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION]
        // In some cases, a matching Activity may not exist, so ensure you safeguard against this.
        // example: not yet supported on WearOS: https://issuetracker.google.com/issues/299174252
        if (intent.resolveActivity(requireActivity().packageManager) != null) {
            Timber.i("launching ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION")
            launch(intent)
        } else {
            openAppSettingsScreenForPermission(MANAGE_EXTERNAL_STORAGE)
        }
    }

    /**
     * Set this PermissionItem so that when it is clicked, the app requests external file management permissions
     * from the user.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    protected fun PermissionsItem.requestExternalStorageOnClick(launcher: ActivityResultLauncher<Intent>) {
        setOnPermissionsRequested { areAlreadyGranted ->
            if (!areAlreadyGranted) launcher.showManageAllFilesScreen()
        }
    }

    /**
     * If these permissions are already granted, open the OS settings to allow the user to disable them, as
     * it is impossible to programmatically revoke a permission. If the permissions have not been granted,
     * execute the callback.
     */
    protected fun PermissionsItem.revokeIfGrantedOnClickElse(callback: () -> Unit) {
        setOnPermissionsRequested { areAlreadyGranted ->
            if (areAlreadyGranted) {
                showToastAndOpenAppSettingsScreenForPermission(permissions.singleOrNull(), CommonString.revoke_permissions)
            } else {
                callback()
            }
        }
    }

    companion object {
        const val REQUEST_PERMISSIONS = "PERMISSION_FRAGMENT_RESULT"
        const val RESULT_HAS_ALL_PERMISSIONS = "HAS_ALL_PERMISSIONS"
    }
}
