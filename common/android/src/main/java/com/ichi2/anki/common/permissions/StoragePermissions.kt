// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2025 David Allison <davidallisongithub@gmail.com>
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.common.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.RequiresApi
import com.ichi2.anki.common.utils.android.isRobolectric

const val MANAGE_EXTERNAL_STORAGE = "android.permission.MANAGE_EXTERNAL_STORAGE"

@RequiresApi(Build.VERSION_CODES.R)
fun isExternalStorageManager(): Boolean {
    // BUG: Environment.isExternalStorageManager() crashes under robolectric
    // https://github.com/robolectric/robolectric/issues/7300
    if (isRobolectric) {
        return false // TODO: handle tests with both 'true' and 'false'
    }
    return Environment.isExternalStorageManager()
}

/**
 * On < Android 11, returns false.
 * On >= Android 11, returns [isExternalStorageManager].
 */
fun isExternalStorageManagerCompat(): Boolean =
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        false
    } else {
        isExternalStorageManager()
    }

/**
 * Check if we have read and write access permission to the external storage.
 * Note: This can return true >= R on a debug build or if storage is preserved.
 */
fun hasLegacyStorageAccessPermission(context: Context): Boolean =
    hasStorageReadAccessPermission(context) && hasStorageWriteAccessPermission(context)

private fun hasStorageReadAccessPermission(context: Context): Boolean = hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)

private fun hasStorageWriteAccessPermission(context: Context): Boolean = hasPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
