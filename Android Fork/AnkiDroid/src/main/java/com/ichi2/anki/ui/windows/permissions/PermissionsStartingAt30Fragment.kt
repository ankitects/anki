// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.anki.common.permissions.MANAGE_EXTERNAL_STORAGE
import com.ichi2.anki.databinding.FragmentPermissionsStartingAt30Binding
import com.ichi2.utils.Permissions.canManageExternalStorage

/**
 * Permissions screen for requesting permissions at and above API 30,
 * if the user [canManageExternalStorage], which isn't possible in the play store.
 *
 * Requested permissions:
 * 1. All files access: [MANAGE_EXTERNAL_STORAGE].
 *   Used for saving the collection in a public directory
 *   which isn't deleted when the app is uninstalled
 */
@RequiresApi(Build.VERSION_CODES.R)
class PermissionsStartingAt30Fragment : PermissionsFragment(R.layout.fragment_permissions_starting_at_30) {
    private val accessAllFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            if (hasAllPermissions()) {
                // the activity records the storage decision and finishes
                setFragmentResult(PermissionsActivity.REQUEST_PERMISSIONS_GRANTED, Bundle())
            }
        }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ) = FragmentPermissionsStartingAt30Binding
        .inflate(inflater, container, false)
        .apply {
            allFilesPermission
                .requestExternalStorageOnClick(accessAllFilesLauncher)
        }.root
}
