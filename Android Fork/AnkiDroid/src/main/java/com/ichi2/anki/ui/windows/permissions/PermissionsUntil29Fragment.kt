// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentPermissionsUntil29Binding
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreenForPermission

/**
 * Permissions screen for requesting permissions until API 29.
 *
 * Requested permissions:
 * 1. Storage access: [Permissions.legacyStorageAccessStartupPermissions].
 *   Used for saving the collection in a public directory
 *   which isn't deleted when the app is uninstalled
 */
class PermissionsUntil29Fragment : PermissionsFragment(R.layout.fragment_permissions_until_29) {
    private val storageLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { requestedPermissions ->
            if (!requestedPermissions.all { it.value }) {
                // The permission dialog did not show up of the user denied the permission.
                // Offers to open the OS settings section for AnkiDroid. In this section, the user can
                // manually grant the permission.
                showToastAndOpenAppSettingsScreenForPermission(
                    requestedPermissions.keys.singleOrNull(),
                    CommonString.startup_no_storage_permission,
                )
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = FragmentPermissionsUntil29Binding
        .inflate(inflater, container, false)
        .apply {
            storagePermission.setOnPermissionsRequested { areAlreadyGranted ->
                if (areAlreadyGranted) return@setOnPermissionsRequested
                if (userCanGrantWriteExternalStorage()) {
                    storageLauncher.launch(storagePermission.permissions.toTypedArray())
                } else {
                    AndroidPermanentlyRevokedPermissionsDialog.show(requireActivity() as AnkiActivity)
                }
            }
        }.root

    // On SDK 33 (TIRAMISU), `WRITE_EXTERNAL_STORAGE` cannot be set [after AnkiDroid 2.15]
    // https://github.com/ankidroid/Anki-Android/issues/14423#issuecomment-1777504376
    private fun userCanGrantWriteExternalStorage() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}
