// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.view.isVisible
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.permissions.LEGACY_POST_NOTIFICATIONS
import com.ichi2.anki.databinding.FragmentAllPermissionsExplanationBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.notificationsPermission
import com.ichi2.utils.Permissions.requestPermissionThroughDialogOrSettings
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreenForPermission
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Permissions explanation screen that appears when the user clicks on the extra info buttons next to the permissions
 * AnkiDroid requests in the OS settings screen. Explains the permissions AnkiDroid requests and provides switches for
 * toggling them on or off.
 *
 * See [the docs](https://developer.android.com/training/permissions/explaining-access#privacy-dashboard).
 */
@RequiresApi(Build.VERSION_CODES.S)
class AllPermissionsExplanationFragment : PermissionsFragment(R.layout.fragment_all_permissions_explanation) {
    private val binding by viewBinding(FragmentAllPermissionsExplanationBinding::bind)

    /**
     * Attempts to open the dialog for granting permissions. Falls back to opening the OS settings if the dialog fails to
     * show up or if the permissions are rejected by the user. The dialog may fail to show up if the user has previously denied the
     * permissions multiple times, if the user selects "don't ask again" on the permissions dialog, etc.
     */
    private val permissionRequestLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted -> Timber.i("Permission result: $isGranted") }

    /**
     * Activity launcher for the external storage management permission.
     */
    private val accessAllFilesLauncher =
        registerForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {}

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        super.onViewCreated(view, savedInstanceState)

        val shouldRequestExternalStorage = Permissions.canManageExternalStorage(requireContext())
        if (shouldRequestExternalStorage) {
            binding.manageExternalStoragePermissionItem.apply {
                isVisible = true
                requestExternalStorageOnClick(accessAllFilesLauncher)
            }
        }
        binding.headingRequiredPermissions.isVisible = shouldRequestExternalStorage

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            binding.postNotificationPermissionItem.apply {
                isVisible = true
                // If it's already granted, offer to revoke it on click; otherwise, request it
                revokeIfGrantedOnClickElse {
                    requestPermissionThroughDialogOrSettings(
                        activity = requireActivity(),
                        permission = notificationsPermission,
                        permissionRequestedFlag = Prefs::notificationsPermissionRequested,
                        permissionRequestLauncher = permissionRequestLauncher,
                    )
                }
            }
        } else {
            binding.legacyPostNotificationPermissionItem.apply {
                isVisible = true
                // If it's already granted, offer to revoke it on click; otherwise, request it
                revokeIfGrantedOnClickElse {
                    showToastAndOpenAppSettingsScreenForPermission(LEGACY_POST_NOTIFICATIONS, CommonString.manually_grant_permissions)
                }
            }
        }

        binding.recordAudioPermissionItem.apply {
            isVisible = true
            // If it's already granted, offer to revoke it on click; otherwise, request it
            revokeIfGrantedOnClickElse {
                requestPermissionThroughDialogOrSettings(
                    activity = requireActivity(),
                    permission = Permissions.RECORD_AUDIO_PERMISSION,
                    permissionRequestedFlag = Prefs::recordAudioPermissionRequested,
                    permissionRequestLauncher = permissionRequestLauncher,
                )
            }
        }
    }
}
