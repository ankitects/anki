// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.startup.getDefaultAnkiDroidDirectory
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Manages the creation, loading, and switching of user profiles.
 * Acts as the single source of truth for the current profile state.
 */
class ProfileManager private constructor(
    context: Context,
) {
    private val profileContext = context.applicationContext ?: context

    lateinit var activeProfileContext: ProfileContextWrapper
        private set

    private val switching = AtomicBoolean(false)

    val isSwitching: Boolean
        get() = switching.get()

    fun tryStartSwitch(): Boolean = switching.compareAndSet(false, true)

    fun endSwitch() = switching.set(false)

    fun isActive(profileId: ProfileId): Boolean = profileId == activeProfileContext.profileId

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
     * @throws IllegalArgumentException if another profile already uses [displayName], ignoring case.
     * @throws Exception if profile creation or persistence fails.
     */
    fun createNewProfile(displayName: ProfileName): ProfileId {
        require(!isNameTaken(displayName)) { "Profile name is already in use" }
        val newProfileId = generateUniqueProfileId()

        val metadata = ProfileMetadata(displayName = displayName)

        profileRegistry.saveProfile(newProfileId, metadata)

        Timber.i("Created new profile: ${newProfileId.value}")
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

    fun wrap(context: Context): Context = activeProfileContext.wrap(context)

    private fun isNameTaken(
        name: ProfileName,
        ignoring: ProfileId? = null,
    ): Boolean = name.existsIn(getAllProfiles().filterKeys { it != ignoring }.values.map { it.displayName.value })

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
     * @return false if the new profile could not be set up or the switch could not be saved,
     *   in which case the current profile stays active
     */
    @VisibleForTesting
    context(_: ProfileSwitchContext)
    fun switchActiveProfile(newProfileId: ProfileId): Boolean {
        Timber.i("Switching profile to ID: $newProfileId")
        try {
            prepareProfile(newProfileId)
        } catch (e: Exception) {
            Timber.w(e, "Could not set up $newProfileId, staying on the current profile")
            return false
        }
        // commit, not apply: the app restarts straight after a switch, and a pending apply() would be lost
        return profileRegistry.commitLastActiveProfileId(newProfileId).also { saved ->
            if (!saved) Timber.w("Could not save the switch to $newProfileId")
        }
    }

    fun restoreRunningProfile() {
        val runningProfileId = activeProfileContext.profileId
        Timber.i("Restoring the running profile: $runningProfileId")
        if (!profileRegistry.commitLastActiveProfileId(runningProfileId, revertOnFailure = false)) {
            Timber.w("Could not restore $runningProfileId as the active profile, retrying in the background")
            profileRegistry.setLastActiveProfileId(runningProfileId)
        }
    }

    private fun prepareProfile(profileId: ProfileId) {
        val wrapper = ProfileContextWrapper.create(profileContext, profileId, resolveProfileDirectory(profileId).file)
        ensureProfileCollectionPath(wrapper)
        if (profileId.isDefault()) return
        CollectionHelper.initializeAnkiDroidDirectory(CollectionHelper.getCurrentAnkiDroidDirectory(wrapper))
    }

    private fun loadProfileData(profileId: ProfileId) {
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
            configureWebView(ProfileId.DEFAULT)
            throw RuntimeException("Failed to load profile environment", e)
        }

        configureWebView(profileId)

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
            getDefaultAnkiDroidDirectory(
                profileContext,
                directoryName = profileId.value,
                folder = AnkiDroidFolder.APP_PRIVATE,
            ).apply { mkdirs() }

        prefs.edit { putString(PREF_COLLECTION_PATH, profileCollectionDir.absolutePath) }
    }

    /**
     * Records [profileId] as the owner of the shared WebView storage, clearing the cookies of the
     * previous owner first.
     *
     * [WebView.setDataDirectorySuffix] is unavailable before API 28, so every profile shares one
     * WebView data directory and a profile change has to clear it. No recorded owner means the
     * first run of a build which records one: if Default is also the only profile then no other
     * profile has ever run here, so the existing data is adopted rather than cleared, which would
     * otherwise touch the WebView on the startup path and drop the AnkiWeb session of users who
     * never switch profiles.
     */
    private fun resetWebViewDataIfProfileChanged(profileId: ProfileId) {
        val webViewOwner = profileRegistry.getWebViewProfileId()
        if (webViewOwner == profileId) return

        if (webViewOwner == null && profileId.isDefault() && profileRegistry.isOnlyProfile(profileId)) {
            Timber.i("Adopting the existing WebView data: no other profile has run on this install")
            profileRegistry.setWebViewProfileId(profileId)
            return
        }

        Timber.i("WebView data belongs to another profile, clearing cookies")
        CookieManager.getInstance().removeAllCookiesSync {
            profileRegistry.setWebViewProfileId(profileId)
        }
    }

    /** Runs [block] once the removal has completed and the cookies are flushed to disk. */
    private fun CookieManager.removeAllCookiesSync(block: CookieManager.() -> Unit) =
        removeAllCookies {
            flush()
            block()
        }

    private fun configureWebView(profileId: ProfileId) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            resetWebViewDataIfProfileChanged(profileId)
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
     *   exist in the registry, or another profile already uses
     *   [newDisplayName], ignoring case.
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

        require(!isNameTaken(newDisplayName, ignoring = profileId)) { "Profile name is already in use" }

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
     * `getDefaultAnkiDroidDirectory(profileContext, directoryName = ...)`
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
            val id = globalPrefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null)
            return id?.let { ProfileId(it) }
        }

        fun setLastActiveProfileId(id: ProfileId) {
            globalPrefs.edit { putString(PREF_LAST_ACTIVE_PROFILE_ID, id.value) }
        }

        /** @return whether [id] was saved to disk. If it wasn't and [revertOnFailure] is set, the previous ID is put back. */
        fun commitLastActiveProfileId(
            id: ProfileId,
            revertOnFailure: Boolean = true,
        ): Boolean {
            val previous = globalPrefs.getString(PREF_LAST_ACTIVE_PROFILE_ID, null)
            val editor = globalPrefs.edit()
            editor.putString(PREF_LAST_ACTIVE_PROFILE_ID, id.value)
            if (editor.commit()) return true
            if (revertOnFailure) globalPrefs.edit { putString(PREF_LAST_ACTIVE_PROFILE_ID, previous) }
            return false
        }

        fun getWebViewProfileId(): ProfileId? {
            val id = globalPrefs.getString(PREF_WEBVIEW_PROFILE_ID, null)
            return id?.let { ProfileId(it) }
        }

        fun setWebViewProfileId(id: ProfileId) {
            globalPrefs.edit { putString(PREF_WEBVIEW_PROFILE_ID, id.value) }
        }

        /**
         * Whether [id] is the only profile in the registry.
         *
         * Counts raw keys rather than [getAllProfiles] so that an entry which fails to parse still
         * counts as a profile.
         */
        fun isOnlyProfile(id: ProfileId): Boolean {
            val profileKeys = globalPrefs.all.keys - BOOKKEEPING_KEYS
            return profileKeys.size == 1 && id.value in profileKeys
        }

        fun saveProfile(
            id: ProfileId,
            metadata: ProfileMetadata,
            isActive: Boolean = false,
        ) {
            globalPrefs.edit {
                putString(id.value, metadata.toJson())
                if (isActive) {
                    putString(PREF_LAST_ACTIVE_PROFILE_ID, id.value)
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
         * [PREF_LAST_ACTIVE_PROFILE_ID] metadata key, and deserializes each
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
                if (key in BOOKKEEPING_KEYS) continue

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

    data class LoadFailure(
        val profileName: ProfileName,
        val defaultProfileName: ProfileName,
    )

    companion object {
        private const val MAX_ATTEMPTS = 10
        const val PROFILE_REGISTRY_FILENAME = "profiles_prefs"
        const val PREF_LAST_ACTIVE_PROFILE_ID = "last_active_profile_id"

        /** Profile whose data the shared WebView storage currently holds. Pre-API 28 only. */
        const val PREF_WEBVIEW_PROFILE_ID = "webview_profile_id"

        /** Registry keys that are not profile entries. */
        private val BOOKKEEPING_KEYS = setOf(PREF_LAST_ACTIVE_PROFILE_ID, PREF_WEBVIEW_PROFILE_ID)

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
         * Failure from the last [createOrNull], if any.
         *
         * Timber is not planted during [android.app.Application.attachBaseContext],
         * so a failure there cannot be logged where it happens.
         */
        var attachError: Throwable? = null
            private set

        var loadFailure: LoadFailure? = null
            private set

        private var unreportedLoadFailure: LoadFailure? = null

        fun consumeLoadFailure(): LoadFailure? = unreportedLoadFailure.also { unreportedLoadFailure = null }

        @CheckResult
        @SuppressLint("UseKtx") // edit {} drops the commit result, which tells the dialog whether it can close
        fun selectDefaultProfile(context: Context): Boolean =
            context
                .getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                .edit()
                .putString(PREF_LAST_ACTIVE_PROFILE_ID, ProfileId.DEFAULT.value)
                .commit()

        /**
         * Loads the active profile, or returns null when its environment cannot be
         * loaded.
         *
         * Never throws: a failure during [android.app.Application.attachBaseContext]
         * would stop the app from starting at all, so callers run on the base context
         * instead. Users with only the Default profile are unaffected either way, since
         * the wrapper delegates everything to the base context for that profile.
         */
        fun createOrNull(base: Context): ProfileManager? =
            try {
                attachError = null
                loadFailure = null
                unreportedLoadFailure = null
                create(base)
            } catch (e: Exception) {
                attachError = e
                loadFailure = readLoadFailure(base)
                unreportedLoadFailure = loadFailure
                null
            }

        private fun readLoadFailure(base: Context): LoadFailure? {
            try {
                val registryPrefs = (base.applicationContext ?: base).getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                val registry = ProfileRegistry(registryPrefs)
                val failedId = registry.getLastActiveProfileId()?.takeUnless { it.isDefault() } ?: return null
                return LoadFailure(
                    profileName = registry.getProfileMetadata(failedId)?.displayName ?: ProfileName.fromTrustedSource(failedId.value),
                    defaultProfileName =
                        registry.getProfileMetadata(ProfileId.DEFAULT)?.displayName
                            ?: ProfileName.fromTrustedSource(DEFAULT_PROFILE_DISPLAY_NAME),
                )
            } catch (e: Exception) {
                return null
            }
        }

        @VisibleForTesting
        fun resetForTesting() {
            attachError = null
            loadFailure = null
            unreportedLoadFailure = null
        }

        /**
         * Factory method to safely create and initialize the ProfileManager.
         * Guaranteed to return a ProfileManager with a valid [activeProfileContext].
         *
         * @param context an application-level context. Safe to call from
         *   [android.app.Application.attachBaseContext], where the base context is
         *   passed directly because [Context.getApplicationContext] is still null.
         */
        fun create(context: Context): ProfileManager {
            val manager = ProfileManager(context)
            manager.initializeActiveProfile()
            return manager
        }
    }
}
