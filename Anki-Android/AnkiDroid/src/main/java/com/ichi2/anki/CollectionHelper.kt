// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.VisibleForTesting
import com.ichi2.anki.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.CollectionHelper.getCurrentAnkiDroidDirectory
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.utils.android.isInstrumentationTest
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.StorageNotConfiguredException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.storage.StorageDecision
import timber.log.Timber
import java.io.File
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
     * @return whether or not dir is accessible: `false` if inaccessible, not yet configured,
     * or the system could not provide a storage location
     * @param context to get directory with
     */
    fun isCurrentAnkiDroidDirAccessible(context: Context): Boolean =
        try {
            initializeAnkiDroidDirectory(getCurrentAnkiDroidDirectory(context))
            true
        } catch (e: StorageAccessException) {
            Timber.w(e)
            false
        } catch (e: StorageNotConfiguredException) {
            Timber.w(e)
            false
        } catch (e: SystemStorageException) {
            Timber.w(e)
            false
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
     * Set when startup failed to choose a default collection path because Android could not
     * provide a storage location ([SystemStorageException]: OS bug/SD card issue).
     *
     * Reads of an unset collection path rethrow this instead of [StorageNotConfiguredException],
     * so the storage failure is not mistaken for the expected 'no collection path set' state.
     */
    // TODO: #19552 - consolidate with AnkiDroidApp.fatalError and StorageDecision (a dedicated
    //  'storage unavailable' state) once the storage setup flow exists
    var systemStorageFailure: SystemStorageException? = null

    /**
     * Whether the location of the collection has been decided: [StorageDecision.Decided] once
     * [PREF_COLLECTION_PATH] is set (or a [ankiDroidDirectoryOverride] is active), which mirrors
     * when [getCurrentAnkiDroidDirectory] can return a directory rather than throwing.
     *
     * The user is not asked yet: until the dedicated setup flow exists (#19552), the 'decision'
     * is made on their behalf during startup by
     * [ensureCollectionPathSet][com.ichi2.anki.startup.ensureCollectionPathSet], so this is
     * [StorageDecision.Decided] by the time the collection is opened.
     *
     * @param preferences the preferences the collection path will be read from: pass the same
     * (profile) context's preferences as the [getCurrentAnkiDroidDirectory] call being gated
     *
     * TODO: What if a user revokes full storage?
     */
    fun storageDecision(preferences: SharedPreferences): StorageDecision {
        storageDecisionTestOverride?.let { return it }
        if (ankiDroidDirectoryOverride != null) return StorageDecision.Decided
        // a recorded systemStorageFailure also leaves the path unset: startup has already
        // surfaced it via AnkiDroidApp.fatalError, and reads rethrow it rather than
        // reporting 'not configured'
        return if (preferences.contains(PREF_COLLECTION_PATH)) StorageDecision.Decided else StorageDecision.Undecided
    }

    /**
     * @return the absolute path to the AnkiDroid directory.
     *
     * @throws StorageNotConfiguredException if no collection path has been set
     * ([PREF_COLLECTION_PATH] is unset): a default is chosen during startup by
     * [ensureCollectionPathSet][com.ichi2.anki.startup.ensureCollectionPathSet]
     * @throws SystemStorageException if startup failed to choose a default collection path
     * ([systemStorageFailure])
     */
    fun getCurrentAnkiDroidDirectory(context: Context): File = getCurrentAnkiDroidDirectory(context.sharedPrefs())

    fun getCollectionPaths(context: Context): CollectionFiles = CollectionFiles.FolderBasedCollection(getCurrentAnkiDroidDirectory(context))

    // TODO: Duplicates collection.mediaFolder
    fun getMediaDirectory(context: Context) = getCollectionPaths(context).requireMediaFolder()

    /**
     * @return the absolute path to the AnkiDroid directory.
     *
     * @throws StorageNotConfiguredException if no collection path has been set
     * ([PREF_COLLECTION_PATH] is unset): a default is chosen during startup by
     * [ensureCollectionPathSet][com.ichi2.anki.startup.ensureCollectionPathSet]
     * @throws SystemStorageException if startup failed to choose a default collection path
     * ([systemStorageFailure])
     */
    fun getCurrentAnkiDroidDirectory(preferences: SharedPreferences): File {
        val collectionDirectory = preferences.getString(PREF_COLLECTION_PATH, null)
        return if (isInstrumentationTest) {
            // create an "androidTest" directory inside the current collection directory which contains the test data
            // "/AnkiDroid/androidTest" would be a new collection path
            File(collectionDirectory ?: throw collectionPathUnset(), "androidTest")
        } else {
            ankiDroidDirectoryOverride
                ?: File(collectionDirectory ?: throw collectionPathUnset())
        }
    }

    /**
     * The collection path is unset: either the expected pre-setup state
     * ([StorageNotConfiguredException]) or startup failed to choose a default and the recorded
     * [SystemStorageException] is rethrown.
     */
    private fun collectionPathUnset(): Exception = systemStorageFailure ?: StorageNotConfiguredException()

    /** Test-only override for [storageDecision]. @see ankiDroidDirectoryOverride */
    @VisibleForTesting
    var storageDecisionTestOverride: StorageDecision? = null
}
