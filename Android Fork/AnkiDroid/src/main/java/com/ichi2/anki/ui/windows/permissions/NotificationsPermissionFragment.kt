// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.ui.windows.permissions

import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.anki.common.permissions.canPostNotifications
import com.ichi2.anki.databinding.FragmentNotificationsPermissionBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.notificationsPermission
import com.ichi2.utils.Permissions.requestPermissionThroughDialogOrSettings
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Permissions fragment shown on the [PermissionsBottomSheet] for requesting notification permissions
 * from the user. This permission only needs to be requested at or above API 33.
 *
 * Requested permissions:
 * 1. Notifications: [Permissions.notificationsPermission].
 *   Used to view and cancel sync progress.
 *   Used for review reminder notifications.
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
class NotificationsPermissionFragment : PermissionsFragment(R.layout.fragment_notifications_permission) {
    private val binding by viewBinding(FragmentNotificationsPermissionBinding::bind)

    /**
     * Launches the OS dialog for requesting notification permissions.
     */
    private val notificationPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { isGranted -> Timber.i("Notification permission result: $isGranted") }

    override fun onResume() {
        super.onResume()
        // onResume is called after returning from both the OS settings and the OS permission request dialog
        if (canPostNotifications(requireContext())) {
            // Post a fragment result to indicate that the bottom sheet can be dismissed
            setFragmentResult(PermissionsBottomSheet.REQUEST_DISMISS_PERMISSIONS_SHEET, Bundle())
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.notificationPermission.revokeIfGrantedOnClickElse {
            requestPermissionThroughDialogOrSettings(
                activity = requireActivity(),
                permission = notificationsPermission,
                permissionRequestedFlag = Prefs::notificationsPermissionRequested,
                permissionRequestLauncher = notificationPermissionLauncher,
            )
        }
    }
}
