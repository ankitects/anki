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

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentPermissionsUntil29Binding
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreen

/**
 * Permissions screen for requesting permissions until API 29.
 *
 * Requested permissions:
 * 1. Storage access: [Permissions.legacyStorageAccessPermissions].
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
                showToastAndOpenAppSettingsScreen(R.string.startup_no_storage_permission)
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = FragmentPermissionsUntil29Binding
        .inflate(inflater, container, false)
        .apply {
            internetPermission.initializeInternetPermissionItem()
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
