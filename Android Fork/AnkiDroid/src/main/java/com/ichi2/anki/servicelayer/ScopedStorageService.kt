// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.servicelayer

import android.content.Context
import android.os.Build
import android.os.Environment
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.ui.windows.managespace.isInsideDirectoriesRemovedWithTheApp
import com.ichi2.utils.Permissions

object ScopedStorageService {
    /**
     * Whether the user's current collection is now inaccessible due to a 'reinstall'
     *
     * @return `false` if:
     * * ⚠️ The directory will be **removed** on uninstall
     *    * The user installed with Android 11+, and is more likely to expect this behavior
     *    * Note: The directory data may not be removed if the user taps "Keep data" when uninstalling
     * * The collection is currently accessible
     * * the user is on Android 9 or below and Android will not revoke permissions
     * * The user has the potential to grant [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE]
     * @see android.R.attr.preserveLegacyExternalStorage
     * @see android.R.attr.requestLegacyExternalStorage
     */
    fun collectionWasMadeInaccessibleAfterUninstall(context: Context): Boolean {
        // If we're < Q then `requestLegacyExternalStorage` was not introduced
        // We do not check for == Q here, instead relying on `isExternalStorageLegacy`
        // requestLegacyExternalStorage is a strong assumption, but we need to handle the case that
        // this assumption breaks down
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return false
        }

        // the user could obtain MANAGE_EXTERNAL_STORAGE
        if (Permissions.canManageExternalStorage(context)) {
            return false
        }

        if (userIsPromptedToDeleteCollectionOnUninstall(context)) {
            return false
        }

        return !Environment.isExternalStorageLegacy()
    }

    /**
     * Whether the user's current collection will be inaccessible after uninstalling the app
     *
     * DEPRECATED. Use [com.ichi2.anki.services.getMediaMigrationState] instead.
     *
     * @return `false` if:
     * * ⚠️ The directory will be **removed** on uninstall
     *    * The user installed with Android 11+, and is more likely to expect this behavior
     *    * Note: The directory data may not be removed if the user taps "Keep data" when uninstalling
     * * The collection is now inaccessible
     * * the user is on Android Q or below and Android **should** not revoke permissions
     * * The user has the potential to grant [android.Manifest.permission.MANAGE_EXTERNAL_STORAGE]
     * Returns `true` > Android 10 and the user has no way to access the collection on uninstall
     * except for using another build of `com.ichi2.anki` or manually copying files
     * @see android.R.attr.preserveLegacyExternalStorage
     * @see android.R.attr.requestLegacyExternalStorage
     */
    fun collectionWillBeMadeInaccessibleAfterUninstall(context: Context): Boolean {
        // If we're < Q then `requestLegacyExternalStorage` was not introduced
        // If we're == Q then `preserveLegacyExternalStorage` is expected to be in place
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            return false
        }

        // the user could obtain MANAGE_EXTERNAL_STORAGE
        if (Permissions.canManageExternalStorage(context)) {
            return false
        }

        if (userIsPromptedToDeleteCollectionOnUninstall(context)) {
            return false
        }

        return Environment.isExternalStorageLegacy()
    }

    fun userIsPromptedToDeleteCollectionOnUninstall(context: Context): Boolean =
        CollectionHelper.getCollectionPath(context).isInsideDirectoriesRemovedWithTheApp(
            context,
        )
}
