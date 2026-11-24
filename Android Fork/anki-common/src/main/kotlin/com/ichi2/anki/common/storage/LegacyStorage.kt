// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.common.storage

import android.content.Context
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.permissions.hasLegacyStorageAccessPermission
import com.ichi2.anki.common.permissions.isExternalStorageManagerCompat
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.showThemedToast
import timber.log.Timber
import java.io.File

/**
 * Checks if current directory being used by AnkiDroid to store user data is a Legacy Storage Directory.
 * This directory is stored under [CollectionHelper.PREF_COLLECTION_PATH] in SharedPreferences
 *
 * DEPRECATED. Use either `getMediaMigrationState`, or `isInsideDirectoriesRemovedWithTheApp`.
 *
 * @return `true` if AnkiDroid is storing user data in a Legacy Storage Directory.
 *
 * @throws com.ichi2.anki.exception.SystemStorageException if `getExternalFilesDir` returns null
 */
fun isLegacyStorage(context: Context): Boolean = isLegacyStorage(CollectionHelper.getCurrentAnkiDroidDirectory(context), context)

/**
 * Checks if current directory being used by AnkiDroid to store user data is a Legacy Storage Directory.
 * This directory is stored under [CollectionHelper.PREF_COLLECTION_PATH] in SharedPreferences
 * @return `true` if AnkiDroid is storing user data in a Legacy Storage Directory.
 *
 * @param setCollectionPath if `false`, null is returned. This stops an infinite loop
 * if `isLegacyStorage` is called when obtaining the collection path
 */
fun isLegacyStorage(
    context: Context,
    setCollectionPath: Boolean,
): Boolean? {
    if (!setCollectionPath &&
        !context
            .sharedPrefs()
            .contains(CollectionHelper.PREF_COLLECTION_PATH)
    ) {
        return null
    }
    return isLegacyStorage(CollectionHelper.getCurrentAnkiDroidDirectory(context), context)
}

/**
 * @return `true` if [currentDirPath] is a Legacy Storage Directory.
 *
 * DEPRECATED. Use `isInsideDirectoriesRemovedWithTheApp`.
 *
 */
fun isLegacyStorage(
    currentDirPath: File,
    context: Context,
): Boolean {
    val internalScopedDirPath =
        CollectionHelper.getAppSpecificInternalAnkiDroidDirectory(context)
    val currentDir = currentDirPath.canonicalFile
    val externalScopedDirs =
        CollectionHelper.getAppSpecificExternalDirectories(context).map { it.canonicalFile }
    val internalScopedDir = File(internalScopedDirPath).canonicalFile
    Timber.i(
        "isLegacyStorage(): current dir: %s\nscoped external dirs: %s\nscoped internal dir: %s",
        currentDirPath,
        externalScopedDirs.joinToString(", "),
        internalScopedDirPath,
    )

    // Loop to check if the current AnkiDroid directory or any of its parents are the same as the root directories
    // for app-private external or internal storage - the only directories which will be accessible without
    // permissions under scoped storage
    val scopedDirectories = externalScopedDirs + internalScopedDir
    var currentDirParent: File? = currentDir
    while (currentDirParent != null) {
        for (scopedDir in scopedDirectories) {
            if (currentDirParent.compareTo(scopedDir) == 0) {
                Timber.i("isLegacyStorage(): false")
                return false
            }
        }
        currentDirParent = currentDirParent.parentFile?.canonicalFile
    }

    // If the current AnkiDroid directory isn't a sub directory of the app-private external or internal storage
    // directories, then it must be in a legacy storage directory
    Timber.i("isLegacyStorage(): true")
    return true
}

/** Checks whether storage permissions are granted on the device. If the device is not using legacy storage,
 *  it verifies if the app has been granted the necessary storage access permission.
 *  @return `true`: if granted, otherwise `false`
 *
 * @throws com.ichi2.anki.exception.SystemStorageException if `getExternalFilesDir` returns null
 */
fun grantedStoragePermissions(context: Context): Boolean =
    !isLegacyStorage(context) ||
        hasLegacyStorageAccessPermission(context) ||
        isExternalStorageManagerCompat()

/** Checks whether storage permissions are granted on the device. If the device is not using legacy storage,
 *  it verifies if the app has been granted the necessary storage access permission.
 *  @return `true`: if granted, otherwise `false` and shows a missing permission toast
 *
 * @throws com.ichi2.anki.exception.SystemStorageException if `getExternalFilesDir` returns null
 */
fun grantedStoragePermissions(
    context: Context,
    showToast: Boolean,
): Boolean {
    val granted = grantedStoragePermissions(context)

    if (!granted && showToast) {
        showThemedToast(context, context.getString(CommonString.intent_handler_failed_no_storage_permission), false)
    }

    return granted
}
