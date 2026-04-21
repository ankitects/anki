/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import com.ichi2.anki.AnkiDroidFolder.AppPrivateFolder
import com.ichi2.anki.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.CollectionHelper.getCurrentAnkiDroidDirectory
import com.ichi2.anki.backend.createDatabaseUsingAndroidFramework
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.exception.UnknownDatabaseVersionException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.DB
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.preferences.getOrSetString
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

object CollectionHelper {
    /**
     * The preference key for the path to the current AnkiDroid directory
     *
     * This directory contains all AnkiDroid data and media for a given collection
     * Except the Android preferences, cached files and [MetaDB]
     *
     * This can be changed by the Preferences screen
     * to allow a user to access a second collection via the same AnkiDroid app instance.
     *
     * The path also defines the collection that the AnkiDroid API accesses
     */
    const val PREF_COLLECTION_PATH = "deckPath"

    fun getCollectionSize(context: Context): Long? =
        try {
            getCollectionPath(context).length()
        } catch (e: Exception) {
            Timber.e(e, "Error getting collection Length")
            null
        }

    /**
     * Create the AnkiDroid directory if it doesn't exist and add a .nomedia file to it if needed.
     *
     * The AnkiDroid directory is a user preference stored under [PREF_COLLECTION_PATH], and a sensible
     * default is chosen if the preference hasn't been created yet (i.e., on the first run).
     *
     * The presence of a .nomedia file indicates to media scanners that the directory must be
     * excluded from their search. We need to include this to avoid media scanners including
     * media files from the collection.media directory. The .nomedia file works at the directory
     * level, so placing it in the AnkiDroid directory will ensure media scanners will also exclude
     * the collection.media sub-directory.
     *
     * @param dir  Directory to initialize
     * @throws StorageAccessException If no write access to directory
     */
    @Synchronized
    @Throws(StorageAccessException::class)
    fun initializeAnkiDroidDirectory(dir: File) {
        // Create specified directory if it doesn't exit
        if (!dir.exists() && !dir.mkdirs()) {
            throw StorageAccessException("Failed to create AnkiDroid directory $dir")
        }
        if (!dir.canWrite()) {
            throw StorageAccessException("No write access to AnkiDroid directory $dir")
        }
        // Add a .nomedia file to it if it doesn't exist
        val nomedia = File(dir, ".nomedia")
        if (!nomedia.exists()) {
            try {
                nomedia.createNewFile()
            } catch (e: IOException) {
                throw StorageAccessException("Failed to create .nomedia file", e)
            }
        }
    }

    /**
     * Try to access the current AnkiDroid directory
     * @return whether or not dir is accessible
     * @param context to get directory with
     */
    fun isCurrentAnkiDroidDirAccessible(context: Context): Boolean =
        try {
            initializeAnkiDroidDirectory(getCurrentAnkiDroidDirectory(context))
            true
        } catch (e: StorageAccessException) {
            Timber.w(e)
            false
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
        val legacyStorage = selectAnkiDroidFolder(context) != AppPrivateFolder
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
     * @return Returns an array of [File]s reflecting the directories that AnkiDroid can access without storage permissions
     * @see android.content.Context.getExternalFilesDirs
     */
    fun getAppSpecificExternalDirectories(context: Context): List<File> = context.getExternalFilesDirs(null)?.filterNotNull() ?: listOf()

    /**
     * Returns the absolute path to the private AnkiDroid directory under the app-specific, internal storage directory.
     *
     *
     * AnkiDroid can access this directory without permissions, even under Scoped Storage
     * Other apps cannot access this directory, regardless of what permissions they have
     *
     * @param context Used to get the Internal App-Specific directory for AnkiDroid
     * @return Returns the absolute path to the App-Specific Internal AnkiDroid Directory
     */
    fun getAppSpecificInternalAnkiDroidDirectory(context: Context): String = context.filesDir.absolutePath

    /**
     * @return the path to the actual [Collection] file
     *
     * @throws UnsupportedOperationException if the collection is in-memory
     */
    fun getCollectionPath(context: Context) = getCollectionPaths(context).requireDiskBasedCollection().colDb

    /** A temporary override for [getCurrentAnkiDroidDirectory] */
    var ankiDroidDirectoryOverride: File? = null

    /**
     * @return the absolute path to the AnkiDroid directory.
     *
     * @throws SystemStorageException if `getExternalFilesDir` returns null
     */
    fun getCurrentAnkiDroidDirectory(context: Context): File =
        getCurrentAnkiDroidDirectoryOptionalContext(context.sharedPrefs()) { context }

    fun getCollectionPaths(context: Context): CollectionFiles = CollectionFiles.FolderBasedCollection(getCurrentAnkiDroidDirectory(context))

    // TODO: Duplicates collection.mediaFolder
    fun getMediaDirectory(context: Context) = getCollectionPaths(context).requireMediaFolder()

    /**
     * An accessor which makes [Context] optional in the case that [PREF_COLLECTION_PATH] is set
     *
     * @return the absolute path to the AnkiDroid directory.
     */
    // This uses a lambda as we typically depends on the `lateinit` AnkiDroidApp.instance
    // If we remove all Android references, we get a significant unit test speedup
    @VisibleForTesting(otherwise = VisibleForTesting.NONE)
    internal fun getCurrentAnkiDroidDirectoryOptionalContext(
        preferences: SharedPreferences,
        context: () -> Context,
    ): File =
        if (AnkiDroidApp.INSTRUMENTATION_TESTING) {
            // create an "androidTest" directory inside the current collection directory which contains the test data
            // "/AnkiDroid/androidTest" would be a new collection path
            val currentCollectionDirectory =
                preferences.getOrSetString(PREF_COLLECTION_PATH) {
                    getDefaultAnkiDroidDirectory(context()).absolutePath
                }
            File(
                currentCollectionDirectory,
                "androidTest",
            )
        } else {
            ankiDroidDirectoryOverride
                ?: File(
                    preferences.getOrSetString(PREF_COLLECTION_PATH) {
                        getDefaultAnkiDroidDirectory(
                            context(),
                        ).absolutePath
                    },
                )
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
        context.sharedPrefs().edit { putString(PREF_COLLECTION_PATH, directory.absolutePath) }
    }

    @Throws(UnknownDatabaseVersionException::class)
    fun getDatabaseVersion(context: Context): Int {
        // backend can't open a schema version outside range, so fall back to a pure DB implementation
        val colPath = getCollectionPath(context)
        if (!colPath.exists()) {
            throw UnknownDatabaseVersionException(FileNotFoundException(colPath.absolutePath))
        }
        var db: DB? = null
        return try {
            db = createDatabaseUsingAndroidFramework(context, colPath)
            db.queryScalar("SELECT ver FROM col")
        } catch (e: Exception) {
            Timber.w(e, "Couldn't open the database to obtain collection version!")
            throw UnknownDatabaseVersionException(e)
        } finally {
            db?.close()
        }
    }
}
