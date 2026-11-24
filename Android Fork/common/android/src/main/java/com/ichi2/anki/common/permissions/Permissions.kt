// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import timber.log.Timber

/**
 * Whether the app is granted [permission].
 *
 * Same as [ContextCompat.checkSelfPermission] except it corrects a bug
 * related to [MANAGE_EXTERNAL_STORAGE].
 */
fun hasPermission(
    context: Context,
    permission: String,
): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && permission == MANAGE_EXTERNAL_STORAGE) {
        // checkSelfPermission doesn't return PERMISSION_GRANTED, even if it's granted.
        return isExternalStorageManager()
    }

    if (permission == LEGACY_POST_NOTIFICATIONS) {
        // hack: just in case hasPermission is ever called with LEGACY_POST_NOTIFICATIONS
        // checkSelfPermission only works on API 33+
        val canPostNotifs = canPostNotifications(context)
        Timber.w(
            "hasPermission called with legacy permissions sentinel; not technically a permission. Returning whether notifications are enabled: $canPostNotifs",
        )
        return canPostNotifs
    }

    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Whether the app is granted all permissions in [permissions].
 */
fun hasAllPermissions(
    context: Context,
    permissions: Collection<String>,
): Boolean = permissions.all { hasPermission(context, it) }

/**
 * Dummy sentinel "permission" string which represents the permission to send notifications on API <33.
 * On API <33, there is no explicit manifest permission for notifications, but they can be toggled off by the user in system settings.
 * See `legacy_post_notification_permission`
 */
const val LEGACY_POST_NOTIFICATIONS: String = "NOTIFICATIONS_BEFORE_API_33_DUMMY_SENTINEL"

fun canPostNotifications(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
