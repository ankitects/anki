/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
 *  Copyright (c) 2022 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.servicelayer

import android.content.Context
import android.os.Build
import android.os.Environment
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.ui.windows.managespace.isInsideDirectoriesRemovedWithTheApp
import com.ichi2.utils.Permissions
import timber.log.Timber
import java.io.File

object ScopedStorageService {
    /**
     * Checks if current directory being used by AnkiDroid to store user data is a Legacy Storage Directory.
     * This directory is stored under [CollectionHelper.PREF_COLLECTION_PATH] in SharedPreferences
     *
     * DEPRECATED. Use either [com.ichi2.anki.services.getMediaMigrationState], or
     *   [com.ichi2.anki.ui.windows.managespace.isInsideDirectoriesRemovedWithTheApp].
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
     * DEPRECATED. Use [com.ichi2.anki.ui.windows.managespace.isInsideDirectoriesRemovedWithTheApp].
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
