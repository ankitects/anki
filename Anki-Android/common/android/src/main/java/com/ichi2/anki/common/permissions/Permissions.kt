// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.permissions

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

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

    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Whether the app is granted all permissions in [permissions].
 */
fun hasAllPermissions(
    context: Context,
    permissions: Collection<String>,
): Boolean = permissions.all { hasPermission(context, it) }
