// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.ui.windows.permissions

import android.os.Bundle
import android.view.View
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.common.permissions.LEGACY_POST_NOTIFICATIONS
import com.ichi2.anki.common.permissions.canPostNotifications
import com.ichi2.anki.databinding.FragmentLegacyNotificationsPermissionBinding
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreenForPermission
import dev.androidbroadcast.vbpd.viewBinding

/**
 * Pre-API 33 counterpart of [NotificationsPermissionFragment], shown on the [PermissionsBottomSheet] for
 * requesting notification permissions from the user.
 *
 * Below API 33 there is no notifications permission to request: notifications are implicitly granted, but the
 * user can still turn them off in the system settings. There is therefore no permission request launcher here,
 * as the only thing we can do is send the user to the system settings to re-enable notifications manually.
 *
 * Requested permissions:
 * 1. Notifications: [LEGACY_POST_NOTIFICATIONS].
 *   Used to view and cancel sync progress.
 *   Used for review reminder notifications.
 */
class LegacyNotificationsPermissionFragment : PermissionsFragment(R.layout.fragment_legacy_notifications_permission) {
    private val binding by viewBinding(FragmentLegacyNotificationsPermissionBinding::bind)

    override fun onResume() {
        super.onResume()
        // onResume is called after returning from the OS settings
        if (canPostNotifications(requireContext())) {
            // Post a fragment result to indicate that the bottom sheet can be dismissed
            setFragmentResult(PermissionsBottomSheet.REQUEST_DISMISS_PERMISSIONS_SHEET, Bundle())
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        binding.legacyNotificationPermission.revokeIfGrantedOnClickElse {
            showToastAndOpenAppSettingsScreenForPermission(LEGACY_POST_NOTIFICATIONS, CommonString.manually_grant_permissions)
        }
    }
}
