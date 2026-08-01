// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.content.Context
import android.os.Environment
import androidx.annotation.CheckResult
import androidx.core.content.edit
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.selectAnkiDroidFolder
import com.ichi2.anki.storage.AnkiDroidFolder
import timber.log.Timber
import java.io.File

/**
 * Ensures [CollectionHelper.PREF_COLLECTION_PATH] is set, choosing and persisting
 * [getDefaultAnkiDroidDirectory] if it is unset.
 *
 * This decides the storage location on the user's behalf: until a storage setup flow exists
 * (#19552), the user is not asked.
 *
 * @throws SystemStorageException if `getExternalFilesDir` returns null. The failure is recorded
 * in [CollectionHelper.systemStorageFailure] so reads report it rather than
 * [StorageNotConfiguredException]
 */
fun ensureCollectionPathSet(context: Context) {
    val preferences = context.sharedPrefs()
    if (preferences.contains(CollectionHelper.PREF_COLLECTION_PATH)) return
    val defaultPath =
        try {
            getDefaultAnkiDroidDirectory(context).absolutePath
        } catch (e: SystemStorageException) {
            CollectionHelper.systemStorageFailure = e
            throw e
        }
    Timber.i("collection path set to default")
    Timber.d("default collection path: %s", defaultPath)
    preferences.edit { putString(CollectionHelper.PREF_COLLECTION_PATH, defaultPath) }
}

/**
 * Get the absolute path to a directory that is suitable to be the default starting location
 * for the AnkiDroid directory.
 *
 * Currently, this is a directory named "AnkiDroid" at the top level of the non-app-specific external storage directory.
 *
 * When targeting API > 29, AnkiDroid will have to use Scoped Storage on any device of any API level.
 * Scoped Storage only allows access to App-Specific directories (without permissions).
 * Hence, AnkiDroid won't be able to access the directory used currently on all devices,
 * regardless of their API level, once AnkiDroid targets API > 29.
 * Instead, AnkiDroid will have to use an App-Specific directory to store the AnkiDroid directory.
 * This applies to the entire AnkiDroid userbase.
 *
 * Currently, if `TESTING_SCOPED_STORAGE` is set to `true`, AnkiDroid uses its External
 * App-Specific directory.
 *
 *
 * External App-Specific Storage is used since the only advantage Internal App-Specific Storage has over External
 * App-Specific storage is additional security, but AnkiDroid does not store sensitive data. Defaulting to
 * External Storage preserves the current behavior of the App
 * (AnkiDroid defaults to External before the Migration To Scoped Storage).
 *
 *
 * TODO: If External Storage isn't emulated, allow users to choose between External & Internal App-Specific Storage
 * instead of defaulting to External App-Specific Storage. This should be done since using either one may be more
 * useful for them. If External Storage is emulated, there is no use in providing the option since Internal
 * Storage can not provide more storage space than External Storage if External Storage is emulated.
 *
 * See the detailed explanation on storage locations & their classification below for more details.
 *
 * App-Specific storage refers to directories which are meant to store files that are meant to be used by a
 * particular app. Each app has its own Internal & External App-Specific directory. Under Scoped Storage,
 * an app can only access its own Internal & External App-Specific directory without needing permissions.
 *
 * Storage can be classified as Internal or External Storage.
 *
 * Internal Storage: This storage is characterized by the fact that it is always available since it always resides
 * on the device's own non-removable storage.
 *
 *
 * App-Specific Internal Storage can be accessed by ONLY the app which owns that directory (without any permissions).
 * It cannot be accessed by any other apps.
 * It cannot be accessed using the Files app on Android or by connecting a device to a pc via USB.
 *
 * External Storage:
 *
 * This storage is characterized only by the fact that it is not guaranteed to be available.
 *
 * It may be built-in, non-removable storage on the device which is being emulated to function like external storage.
 * In this case, it doesn't offer more space than Internal Storage.
 *
 * Or, it may be removable storage like an SD Card.
 *
 * App-Specific External Storage can be accessed by the app it is owned by without any permissions.
 * It can be accessed by any apps with the WRITE_EXTERNAL_STORAGE permission.
 * It can also be accessed via the Android Files app or by connecting the device to a PC via USB.
 *
 * Note: The Files app can be misleading. On Samsung devices, clicking on Internal Storage it actually shows the
 * emulated external storage (/storage/emulated/0/ in my case) - this is because from the point of view of the user,
 * emulated external storage is just more internal storage since it is built into the phone. This is why vendors
 * like Samsung may refer to external emulated storage as internal storage, even though for developers, they mean
 * very different things as explained above.
 *
 * @param directoryName  The leaf folder name to use at the end of the returned path.
 *                       Defaults to `"AnkiDroid"` (the historical default-profile folder name).
 *                       Callers wanting a profile-specific layout can pass e.g. the profile id.
 * @return Absolute Path to the default location starting location for the AnkiDroid directory
 *
 * @throws SystemStorageException if `getExternalFilesDir` returns null
 */
// TODO Tracked in https://github.com/ankidroid/Anki-Android/issues/5304
@CheckResult
fun getDefaultAnkiDroidDirectory(
    context: Context,
    directoryName: String = "AnkiDroid",
): File {
    val legacyStorage = selectAnkiDroidFolder(context) != AnkiDroidFolder.APP_PRIVATE
    return if (legacyStorage) {
        legacyAnkiDroidDirectory(directoryName)
    } else {
        File(getAppSpecificExternalAnkiDroidDirectory(context), directoryName)
    }
}

/**
 * Returns the absolute path to the AnkiDroid directory under the primary/shared external storage directory.
 * This directory may be in emulated external storage, or can be an SD Card directory.
 *
 * @param directoryName  The folder name to use at the end of the returned path. Defaults to
 *                       `"AnkiDroid"`. Non-default profiles can pass `ProfileId` here to get a
 *                       profile-specific layout.
 * @return Absolute path to the AnkiDroid directory in primary shared/external storage
 */
private fun legacyAnkiDroidDirectory(directoryName: String = "AnkiDroid"): File =
    File(Environment.getExternalStorageDirectory(), directoryName)

/**
 * Returns the absolute path to the AnkiDroid directory under the app-specific, primary/shared external storage
 * directory.
 *
 *
 * This directory may be in emulated external storage, or can be an SD Card directory.
 * If it is actually external storage, i.e., removable storage like an SD Card, instead of storage
 * built into the device itself, using this directory over internal storage can be beneficial since
 * it may be able to store more data.
 *
 *
 * AnkiDroid can access this directory without permissions, even under Scoped Storage
 * Other apps can access this directory if they have the WRITE_EXTERNAL_STORAGE permission
 *
 * @param context Used to get the External App-Specific directory for AnkiDroid
 * @return Returns the absolute path to the App-Specific External AnkiDroid directory
 *
 * @throws SystemStorageException if `getExternalFilesDir` returns null
 */
private fun getAppSpecificExternalAnkiDroidDirectory(context: Context): String? {
    val externalFilesDir = context.getExternalFilesDir(null)

    // This value *may* be null but we strictly require it. This has caused NullPointerException
    // in previous releases as we dereference. We can't recover but for purposes of triage,
    // we will now check for null and if so try to log more information about why.
    if (externalFilesDir == null) {
        Timber.e("Attempting to determine collection path, but no valid external storage?")
        throw SystemStorageException.build(
            errorDetail = "getExternalFilesDir unexpectedly returned null",
            infoUri = "https://github.com/ankidroid/Anki-Android/issues/13207",
        )
    }
    return externalFilesDir.absolutePath
}

/**
 * Resets the AnkiDroid directory to [directory]
 * Note: if [android.R.attr.preserveLegacyExternalStorage] is in use
 * this will represent a change from `/AnkiDroid` to `/Android/data/...`
 *
 * @throws SystemStorageException if `getExternalFilesDir` returns null
 */
fun resetAnkiDroidDirectory(
    context: Context,
    directory: File = getDefaultAnkiDroidDirectory(context),
) {
    Timber.d("resetting AnkiDroid directory to %s", directory)
    context.sharedPrefs().edit { putString(CollectionHelper.PREF_COLLECTION_PATH, directory.absolutePath) }
}
