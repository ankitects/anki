/*
 * Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.multiprofile

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ichi2.anki.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.CollectionHelper.getDefaultAnkiDroidDirectory
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.preferences.sharedPrefs
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * Manages the creation, loading, and switching of user profiles.
 * Acts as the single source of truth for the current profile state.
 */
class ProfileManager private constructor(
    context: Context,
) {
    private val profileContext = context.applicationContext

    lateinit var activeProfileContext: Context
        private set

    /**
     * Stores the Registry of all profiles (ID -> Display Name) and the
     * ID of the currently active profile.
     */
    private val globalProfilePrefs by lazy {
        profileContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
    }

    private val profileRegistry by lazy { ProfileRegistry(globalProfilePrefs) }

    /**
     * Internal initialization logic.
     * Finds the correct profile to load (or creates the default) and initializes the environment.
     */
    private fun initializeActiveProfile() {
        val activeProfileId =
            profileRegistry.getLastActiveProfileId()
                ?: initializeDefaultProfile()

        Timber.i("Initializing profile: ${activeProfileId.value}")
        loadProfileData(activeProfileId)
    }

    private fun initializeDefaultProfile(): ProfileId {
        Timber.i("No active profile found. Setting up Default.")
        val defaultId = ProfileId.DEFAULT

        val metadata =
            ProfileMetadata(displayName = ProfileName.fromTrustedSource(DEFAULT_PROFILE_DISPLAY_NAME))

        profileRegistry.saveProfile(id = defaultId, metadata = metadata, isActive = true)
        profileRegistry.setLastActiveProfileId(defaultId)

        return defaultId
    }

    /**
     * Creates a new user profile with the given display name.
     *
     * Generates a unique [ProfileId], constructs the corresponding
     * [ProfileMetadata], and persists it using the [profileRegistry]. The newly
     * created profile is initialized with version `1`.
     *
     * @param displayName The name to be associated with the new profile.
     * @return The unique [ProfileId] assigned to the newly created profile.
     *
     * @throws Exception if profile creation or persistence fails.
     */
    fun createNewProfile(displayName: ProfileName): ProfileId {
        val newProfileId = generateUniqueProfileId()

        val metadata = ProfileMetadata(displayName = displayName)

        profileRegistry.saveProfile(newProfileId, metadata)

        Timber.i("Created new profile: ${displayName.value} (${newProfileId.value})")
        return newProfileId
    }

    /**
     * Returns all registered profiles and their metadata.
     *
     * @return A map of every [ProfileId] to its corresponding
     *   [ProfileMetadata], including the default profile.
     * @see ProfileRegistry.getAllProfiles
     */
    fun getAllProfiles(): Map<ProfileId, ProfileMetadata> = profileRegistry.getAllProfiles()

    /**
     * Generates a unique [ProfileId] that does not collide with existing profiles.
     *
     * @return A unique [ProfileId] not present in the [profileRegistry].
     *
     * @throws IllegalStateException if a unique ID cannot be generated after
     * [MAX_ATTEMPTS] attempts.
     */
    private fun generateUniqueProfileId(): ProfileId {
        var newId: ProfileId
        var collisionCount = 0

        do {
            if (collisionCount >= MAX_ATTEMPTS) {
                // If we hit this, something is critically wrong with our registry
                throw IllegalStateException("Failed to generate a unique Profile ID after $MAX_ATTEMPTS attempts.")
            }

            if (collisionCount == 1) {
                val warningMessage = "Profile ID collision detected! Retrying generation..."
                val silentException = IllegalStateException(warningMessage)

                CrashReportService.sendExceptionReport(silentException, "ProfileManager::generateUniqueProfileId")
                Timber.w(silentException, warningMessage)
            } else if (collisionCount > 1) {
                Timber.w("Profile ID collision still occurring. Retrying... (Attempt ${collisionCount + 1})")
            }

            newId = ProfileId.generate()
            collisionCount++
        } while (profileRegistry.contains(newId))

        return newId
    }

    /**
     * Persists [newProfileId] as the active profile.
     *
     * @param newProfileId The [ProfileId] to activate on next launch.
     */
    @VisibleForTesting
    context(_: ProfileSwitchContext)
    fun switchActiveProfile(newProfileId: ProfileId) {
        Timber.i("Switching profile to ID: $newProfileId")
        profileRegistry.setLastActiveProfileId(newProfileId)
    }

    private fun loadProfileData(profileId: ProfileId) {
        configureWebView(profileId)

        val profileBaseDir = resolveProfileDirectory(profileId)

        try {
            val wrapper =
                ProfileContextWrapper.create(
                    context = profileContext,
                    profileId = profileId,
                    profileBaseDir = profileBaseDir.file,
                )
            activeProfileContext = wrapper
            ensureProfileCollectionPath(wrapper)
        } catch (e: Exception) {
            Timber.w(e, "Failed to load profile context for $profileId")
            throw RuntimeException("Failed to load profile environment", e)
        }

        Timber.d("Profile loaded: $profileId at ${profileBaseDir.file.absolutePath}")
    }

    /**
     * Ensures that a valid collection path is initialized and stored in the profile's shared preferences.
     *
     * For non-default profiles, this method mirrors the standard AnkiDroid directory structure
     * by creating a profile-specific subdirectory within the external files directory.
     *
     * @param wrapper  The [ProfileContextWrapper] providing access to the profile-namespace SharedPreferences.
     *
     * @throws com.ichi2.anki.exception.SystemStorageException
     *   if the app's external storage is unavailable (surfaced by
     *   [getDefaultAnkiDroidDirectory]). The profile cannot be loaded without writable
     *   external storage.
     *
     * @see PREF_COLLECTION_PATH
     */
    private fun ensureProfileCollectionPath(wrapper: ProfileContextWrapper) {
        val profileId = wrapper.profileId
        if (profileId.isDefault()) return

        val prefs = wrapper.sharedPrefs()
        if (prefs.getString(PREF_COLLECTION_PATH, null) != null) return

        val profileCollectionDir =
            getDefaultAnkiDroidDirectory(profileContext, directoryName = profileId.value).apply { mkdirs() }

        prefs.edit { putString(PREF_COLLECTION_PATH, profileCollectionDir.absolutePath) }
    }

    private fun configureWebView(profileId: ProfileId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            CookieManager.getInstance().removeAllCookies(null)
            return
        }

        if (profileId.isDefault()) {
            return
        }

        try {
            WebView.setDataDirectorySuffix(profileId.value)
        } catch (e: Exception) {
            // This usually means the WebView was accessed before the profile was loaded.
            // This represents a potential privacy leak (using default cookies).
            Timber.w(e, "Failed to set WebView directory suffix (WebView already initialized?)")
        }
    }

    /**
     * Resolves the physical file system location for a given profile's data.
     *
     * @param profileId The validated identifier of the profile.
     * @return A [ProfileRestrictedDirectory] representing the root directory for this profile.
     * The directory is guaranteed to be inside the application's private storage.
     *
     * @throws IllegalStateException if the application data root directory cannot be resolved.
     */
    private fun resolveProfileDirectory(profileId: ProfileId): ProfileRestrictedDirectory {
        val appDataRoot =
            ContextCompat.getDataDir(profileContext)
                ?: profileContext.filesDir.parentFile

        if (appDataRoot == null) {
            val e = IllegalStateException("Cannot resolve Application Data Directory")
            Timber.w(e, "Failed to resolve app data root. Device storage might be corrupted or inaccessible.")
            throw e
        }

        val directoryFile =
            if (profileId.isDefault()) {
                appDataRoot
            } else {
                File(appDataRoot, profileId.value)
            }

        return ProfileRestrictedDirectory(directoryFile)
    }

    /**
     * Renames an existing profile by updating its display name in
     * the registry.
     *
     * All other metadata fields (version, creation timestamp) are
     * preserved. The change is persisted immediately.
     *
     * @param profileId      The [ProfileId] of the profile to rename.
     * @param newDisplayName  The new user-facing name.
     *
     * @throws IllegalArgumentException if [profileId] does not
     *   exist in the registry.
     */
    fun renameProfile(
        profileId: ProfileId,
        newDisplayName: ProfileName,
    ) {
        Timber.d("ProfileManager::renameProfile called for $profileId")

        val existing =
            profileRegistry.getProfileMetadata(profileId)
                ?: throw IllegalArgumentException("Profile $profileId not found")

        if (existing.displayName == newDisplayName) {
            Timber.d("Rename skipped: New name matches existing name for $profileId")
            return
        }

        val updated = existing.copy(displayName = newDisplayName)
        profileRegistry.saveProfile(profileId, updated)

        Timber.d("Renamed profile $profileId to '$newDisplayName'")
    }

    /**
     * Permanently deletes a profile, removing its registry entry and associated data
     * across both internal and external storage.
     *
     * - Internal storage: app-private. We own these directories, so bulk deletion is safe.
     *   For non-default profiles: the profile's data directory and every
     *   `profile_<id>_*.xml` SharedPreferences file. For the default profile:
     *   the legacy root folders (`files`, `cache`, `databases`, ...).
     * - Collection directory: user-visible under `Android/data/.../files/` and
     *   relocatable by the user to any path via [PREF_COLLECTION_PATH] (e.g. `/Pictures/`).
     *   We therefore resolve the stored path, and only delete known AnkiDroid artifacts
     *   inside it - never `deleteRecursively()`.
     */
    fun deleteProfile(profileId: ProfileId) {
        /*
         * File-system data is removed before the registry entry. If deletion fails
         * or is interrupted, the registry entry remains so the user can retry.
         *
         * TODO: COULD_BE_BETTER: this method is not synchronized. Two concurrent calls from
         * different threads/coroutines could both pass the require() checks below and
         * race, eventually leaving the registry with zero profiles. Wrap the body in a
         * Mutex (or move to a single-thread dispatcher) once delete is exposed through
         * the UI. Not a concern today: the only entry points are tests and the (yet to
         * land) settings screen, which is single-threaded.
         */
        require(profileRegistry.getLastActiveProfileId() != profileId) {
            "Cannot delete the currently active profile ($profileId). Switch first."
        }
        // the check above should catch it (deleting the only profile
        // means deleting the active one), but guard explicitly against a corrupted
        // last-active pointer leaving the registry with zero profiles.
        require(getAllProfiles().size > 1) {
            "Cannot delete the only remaining profile ($profileId)."
        }

        Timber.i("deleteProfile: starting deletion of %s", profileId)

        val appDataRoot = ContextCompat.getDataDir(profileContext)

        if (profileId.isDefault()) {
            Timber.d("deleteProfile: wiping legacy default-profile data under %s", appDataRoot)
            deleteDefaultProfileDataOnly(appDataRoot)
        } else {
            val profileDir = resolveProfileDirectory(profileId).file
            if (profileDir.exists()) {
                val removed = profileDir.deleteRecursively()
                Timber.d("deleteProfile: removed profile dir %s (success=%b)", profileDir, removed)
            } else {
                Timber.d("deleteProfile: profile dir %s did not exist; skipping", profileDir)
            }
            deleteNamespacedSharedPreferences(appDataRoot, profileId)
        }

        val collectionDir = resolveStoredCollectionDir(profileId)
        if (collectionDir != null) {
            Timber.d("deleteProfile: cleaning collection dir %s", collectionDir)
            deleteCollectionArtifactsSafely(collectionDir)
        } else {
            Timber.d("deleteProfile: no on-disk collection dir to clean for %s", profileId)
        }

        profileRegistry.removeProfile(profileId)
        Timber.i("deleteProfile: completed deletion of %s", profileId)
    }

    /**
     * Deletes every `profile_<id>_*.xml` file inside the app's `shared_prefs` directory.
     * No-op if [appDataRoot] is null or the `shared_prefs` dir doesn't exist.
     */
    private fun deleteNamespacedSharedPreferences(
        appDataRoot: File?,
        profileId: ProfileId,
    ) {
        if (appDataRoot == null) return
        val sharedPrefsDir = File(appDataRoot, "shared_prefs")
        if (!sharedPrefsDir.exists() || !sharedPrefsDir.isDirectory) return

        val prefix = "profile_${profileId.value}_"
        val deletedCount =
            sharedPrefsDir
                .listFiles()
                ?.filter { it.name.startsWith(prefix) }
                ?.count { it.delete() }
                ?: 0
        Timber.d("deleteNamespacedSharedPreferences: removed %d prefs file(s) for %s", deletedCount, profileId)
    }

    /**
     * Returns the collection directory for [profileId], honouring any user relocation via
     * [PREF_COLLECTION_PATH], and only if it currently exists on disk a non-null result
     * is guaranteed to refer to an existing directory. Falls back to the default location if
     * the preference is unset; returns null if neither path resolves to an existing directory
     *
     * Default profile: `<external>/AnkiDroid/` (the historical layout).
     * Non-default profile: `<external>/<profileId>/`.
     */
    private fun resolveStoredCollectionDir(profileId: ProfileId): File? {
        val candidate: File =
            readStoredCollectionPath(profileId)?.let(::File)
                ?: defaultCollectionDirFor(profileId)
                ?: return null

        return candidate.takeIf { it.exists() && it.isDirectory }
    }

    /**
     * The default-location fallback used when the profile has never written `PREF_COLLECTION_PATH`.
     *
     * TODO: consolidate with the profile-creation path this should delegate to
     * `CollectionHelper.getDefaultAnkiDroidDirectory(profileContext, directoryName = ...)`
     * that gives us legacy-storage handling and `SystemStorageException`-on-null for free, and keeps
     * the "where does a profile collection live" decision in a single place shared with
     * `ensureProfileCollectionPath`.
     */
    private fun defaultCollectionDirFor(profileId: ProfileId): File? {
        val externalFilesDir = profileContext.getExternalFilesDir(null) ?: return null
        return if (profileId.isDefault()) {
            File(externalFilesDir, "AnkiDroid")
        } else {
            File(externalFilesDir, profileId.value)
        }
    }

    /**
     * Reads [PREF_COLLECTION_PATH] from the profile's namespaced default SharedPreferences
     * by filename, so we don't need to instantiate a [ProfileContextWrapper] (which has
     * mkdir side effects we don't want in the delete flow).
     *
     * The filename format must stay in sync with [ProfileContextWrapper.getSharedPreferences]
     * and with `deleteNamespacedSharedPreferences`'s `profile_<id>_` prefix.
     *
     * TODO: extract a `ProfilePreferences` accessor (e.g. `prefsForProfile(profileId).collectionPath`)
     */
    private fun readStoredCollectionPath(profileId: ProfileId): String? {
        val defaultPrefsName = "${profileContext.packageName}_preferences"
        val prefsName =
            if (profileId.isDefault()) defaultPrefsName else "profile_${profileId.value}_$defaultPrefsName"
        return profileContext
            .getSharedPreferences(prefsName, Context.MODE_PRIVATE)
            .getString(PREF_COLLECTION_PATH, null)
    }

    /**
     * Deletes only known AnkiDroid collection artifacts inside [collectionDir].
     *
     * AnkiDroid lets the user point the collection path at any directory — including ones
     * they also use for unrelated data, e.g. `/Pictures/`. We therefore never
     * `deleteRecursively()` this directory. Files and subdirectories we don't recognize are
     * left untouched.
     *
     * If [collectionDir] is empty after the sweep, it is also removed. If anything remains,
     * we send a silent crash report
     */
    private fun deleteCollectionArtifactsSafely(collectionDir: File) {
        if (!collectionDir.exists() || !collectionDir.isDirectory) return

        collectionDir.listFiles()?.forEach { entry ->
            when {
                entry.isFile && entry.name in COLLECTION_ARTIFACT_FILES -> entry.delete()
                entry.isDirectory && entry.name in COLLECTION_ARTIFACT_DIRS -> entry.deleteRecursively()
                else -> {
                    val entryType = if (entry.isDirectory) "Directory" else "File"
                    Timber.w("deleteProfile: leaving unknown entry untouched: <$entryType>")
                }
            }
        }

        // `File.delete()` on a directory succeeds iff it's empty, so a successful delete
        // here means we removed every entry we knew about and nothing else was there.
        if (collectionDir.delete()) return

        val remaining = collectionDir.listFiles() ?: return
        val fileCount = remaining.count { it.isFile }
        val dirCount = remaining.count { it.isDirectory }

        val message =
            "deleteCollectionArtifactsSafely left ${remaining.size} unknown entries in collection dir (Files: $fileCount, Dirs: $dirCount)"

        val silent = IllegalStateException(message)
        CrashReportService.sendExceptionReport(
            silent,
            "ProfileManager::deleteCollectionArtifactsSafely",
        )
        Timber.w(silent, message)
    }

    /**
     * Wipes the Default profile's legacy data from the root directories
     * while protecting the subdirectories belonging to other profiles.
     */
    private fun deleteDefaultProfileDataOnly(appDataRoot: File?) {
        if (appDataRoot == null) return

        val defaultFolders =
            listOf(
                "app_webview",
                "databases",
                "files",
                "cache",
                "code_cache",
                "no_backup",
            )

        defaultFolders.forEach { folderName ->
            val folder = File(appDataRoot, folderName)
            if (folder.exists()) {
                folder.deleteRecursively()
            }
        }

        val sharedPrefsDir = File(appDataRoot, "shared_prefs")
        if (sharedPrefsDir.exists() && sharedPrefsDir.isDirectory) {
            sharedPrefsDir.listFiles()?.forEach { file ->
                val fileName = file.name
                val isRegistry = fileName == "$PROFILE_REGISTRY_FILENAME.xml"
                val isOtherProfile = fileName.startsWith("profile_")

                if (!isRegistry && !isOtherProfile) {
                    file.delete()
                }
            }
        }
    }

    /**
     * Holds the meta-data for a profile.
     * Converted to JSON for storage to allow future extensibility (e.g. avatars, themes).
     */
    data class ProfileMetadata(
        val displayName: ProfileName,
        val version: Int = 1,
        val createdTimestamp: String = getTimestamp(TimeManager.time),
    ) {
        fun toJson(): String =
            JSONObject()
                .apply {
                    put("displayName", displayName.value)
                    put("version", version)
                    put("created", createdTimestamp)
                }.toString()

        companion object {
            fun fromJson(jsonString: String): ProfileMetadata {
                val json = JSONObject(jsonString)
                return ProfileMetadata(
                    displayName = ProfileName.fromTrustedSource(json.optString("displayName", "Unknown")),
                    version = json.optInt("version", 1),
                    createdTimestamp = json.optString("created", ""),
                )
            }
        }
    }

    /**
     * Internal abstraction for the Global Profile Registry.
     * Handles the JSON serialization/deserialization.
     */
    private class ProfileRegistry(
        private val globalPrefs: SharedPreferences,
    ) {
        fun getLastActiveProfileId(): ProfileId? {
            val id = globalPrefs.getString(KEY_LAST_ACTIVE_PROFILE_ID, null)
            return id?.let { ProfileId(it) }
        }

        fun setLastActiveProfileId(id: ProfileId) {
            globalPrefs.edit { putString(KEY_LAST_ACTIVE_PROFILE_ID, id.value) }
        }

        fun saveProfile(
            id: ProfileId,
            metadata: ProfileMetadata,
            isActive: Boolean = false,
        ) {
            globalPrefs.edit {
                putString(id.value, metadata.toJson())
                if (isActive) {
                    putString(KEY_LAST_ACTIVE_PROFILE_ID, id.value)
                }
            }
        }

        fun getProfileMetadata(id: ProfileId): ProfileMetadata? {
            val jsonString = globalPrefs.getString(id.value, null) ?: return null
            return try {
                ProfileMetadata.fromJson(jsonString)
            } catch (e: Exception) {
                Timber.w(e, "Failed to parse profile metadata for ${id.value}")
                null
            }
        }

        /**
         * Retrieves all registered profiles from the global SharedPreferences.
         *
         * Iterates over all stored entries, skipping the
         * [KEY_LAST_ACTIVE_PROFILE_ID] metadata key, and deserializes each
         * value into a [ProfileMetadata] instance. Entries that fail to parse
         * are logged and silently skipped.
         *
         * @return A map of [ProfileId] to [ProfileMetadata] for every
         *   successfully parsed profile in the registry.
         */
        fun getAllProfiles(): Map<ProfileId, ProfileMetadata> {
            val result = mutableMapOf<ProfileId, ProfileMetadata>()
            val allEntries = globalPrefs.all
            for ((key, value) in allEntries) {
                // Skip internal bookkeeping keys; only profile entries remain
                if (key == KEY_LAST_ACTIVE_PROFILE_ID) continue

                val metadata =
                    try {
                        ProfileMetadata.fromJson(value as String)
                    } catch (e: Exception) {
                        Timber.w(e, "Skipping corrupt profile entry: $key")
                        continue
                    }
                result[ProfileId(key)] = metadata
            }
            return result
        }

        /**
         * Removes a profile entry from the registry.
         *
         * Does **not** delete the profile's data directory on disk.
         * Callers are responsible for cleaning up file-system resources.
         *
         * @param id The [ProfileId] of the profile to remove.
         */
        fun removeProfile(id: ProfileId) {
            globalPrefs.edit { remove(id.value) }
        }

        fun contains(id: ProfileId): Boolean = globalPrefs.contains(id.value)
    }

    /**
     * A context representing that it is safe to switch profiles
     *
     * - Backups are not occurring
     * - Sync is completed
     * - Collection is not open
     *
     * @see ProfileSwitchGuard
     */
    object ProfileSwitchContext

    companion object {
        private const val MAX_ATTEMPTS = 10
        const val PROFILE_REGISTRY_FILENAME = "profiles_prefs"
        const val KEY_LAST_ACTIVE_PROFILE_ID = "last_active_profile_id"

        const val DEFAULT_PROFILE_DISPLAY_NAME = "Default"

        /** Files AnkiDroid creates directly inside the collection directory. */
        private val COLLECTION_ARTIFACT_FILES =
            setOf(
                "collection.anki2",
                "collection.anki2-journal",
                "collection.anki2-wal",
                "collection.anki2-shm",
                "collection.media.db",
                "collection.media.db-journal",
                "collection.media.db-wal",
                "collection.media.db-shm",
                ".nomedia",
            )

        /** Subdirectories AnkiDroid creates inside the collection directory. */
        private val COLLECTION_ARTIFACT_DIRS =
            setOf(
                "collection.media",
                "media.trash",
                "backup",
                "broken",
            )

        /**
         * Factory method to safely create and initialize the ProfileManager.
         * Guaranteed to return a ProfileManager with a valid [activeProfileContext].
         */
        fun create(context: Context): ProfileManager {
            val manager = ProfileManager(context)
            manager.initializeActiveProfile()
            return manager
        }
    }
}
