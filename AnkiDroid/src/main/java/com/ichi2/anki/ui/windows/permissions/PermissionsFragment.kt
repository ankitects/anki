/*
 *  Copyright (c) 2023 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.permissions

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.core.view.allViews
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.permissions.hasPermission
import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.Permissions.openAppSettingsScreen
import com.ichi2.utils.Permissions.requestPermissionThroughDialogOrSettings
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreen
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

    private val internetLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                // No need to explicitly do anything, onResume handles updating the UI
                Timber.i("Internet permission granted")
            } else {
                Timber.i("Internet permission denied")
                showToastAndOpenAppSettingsScreen(
                    getString(R.string.permission_required_message, getString(R.string.internet_access_title)),
                )
            }
        }

    override fun onResume() {
        super.onResume()
        permissionsItems.forEach { it.updateSwitchCheckedStatus() }
        setFragmentResult(
            PERMISSIONS_FRAGMENT_RESULT_KEY,
            Bundle().apply { putBoolean(HAS_ALL_PERMISSIONS_KEY, hasAllPermissions()) },
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
            openAppSettingsScreen()
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

    @NeedsTest("Shows the permission item when INTERNET permission is denied")
    @NeedsTest("Hides the permission item when INTERNET permission is already granted")
    protected fun PermissionsItem.initializeInternetPermissionItem() {
        if (hasPermission(requireContext(), Manifest.permission.INTERNET)) {
            // If internet permission is already granted (which is the case for most of devices), hide the permission item.
            this.isVisible = false
            return
        }
        // On devices such as Xiaomi, which allow user to deny internet permissions, show internet permission item.
        setOnPermissionsRequested { areAlreadyGranted ->
            if (!areAlreadyGranted) {
                Timber.d("Requesting for internet permission")
                requestPermissionThroughDialogOrSettings(
                    requireActivity(),
                    Manifest.permission.INTERNET,
                    Prefs::internetPermissionRequested,
                    internetLauncher,
                )
            }
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
                showToastAndOpenAppSettingsScreen(R.string.revoke_permissions)
            } else {
                callback()
            }
        }
    }

    companion object {
        const val PERMISSIONS_FRAGMENT_RESULT_KEY = "PERMISSION_FRAGMENT_RESULT"
        const val HAS_ALL_PERMISSIONS_KEY = "HAS_ALL_PERMISSIONS"
    }
}
