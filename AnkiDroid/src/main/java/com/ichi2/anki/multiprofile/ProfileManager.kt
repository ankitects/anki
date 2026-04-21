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
    private val appContext = context.applicationContext

    lateinit var activeProfileContext: Context
        private set

    /**
     * Stores the Registry of all profiles (ID -> Display Name) and the
     * ID of the currently active profile.
     */
    private val globalProfilePrefs by lazy {
        appContext.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
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
                    context = appContext,
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
            getDefaultAnkiDroidDirectory(appContext, directoryName = profileId.value).apply { mkdirs() }

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
            ContextCompat.getDataDir(appContext)
                ?: appContext.filesDir.parentFile

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
