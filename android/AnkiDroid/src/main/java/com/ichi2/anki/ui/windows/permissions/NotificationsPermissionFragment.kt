/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentNotificationsPermissionBinding
import com.ichi2.anki.settings.Prefs
import com.ichi2.utils.Permissions
import com.ichi2.utils.Permissions.requestPermissionThroughDialogOrSettings
import dev.androidbroadcast.vbpd.viewBinding
import timber.log.Timber

/**
 * Permissions fragment shown on the [PermissionsBottomSheet] for requesting notification permissions
 * from the user. This permission only needs to be requested at or above API 33.
 *
 * Requested permissions:
 * 1. Notifications: [Permissions.postNotification].
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
        if (Permissions.canPostNotifications(requireContext())) {
            // Post a fragment result to indicate that the bottom sheet can be dismissed
            setFragmentResult(PermissionsBottomSheet.DISMISS_RESULT_REQUEST_KEY, Bundle())
        }
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?,
    ) {
        Permissions.postNotification?.let {
            binding.notificationPermission.revokeIfGrantedOnClickElse {
                requestPermissionThroughDialogOrSettings(
                    activity = requireActivity(),
                    permission = it,
                    permissionRequestedFlag = Prefs::notificationsPermissionRequested,
                    permissionRequestLauncher = notificationPermissionLauncher,
                )
            }
        }
    }
}
