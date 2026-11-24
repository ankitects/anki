// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabaseCorruptException
import android.database.sqlite.SQLiteFullException
import android.os.Build
import android.os.Environment
import android.os.Parcelable
import androidx.annotation.CheckResult
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import com.ichi2.anki.backend.DatabaseCorruption
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.permissions.LEGACY_POST_NOTIFICATIONS
import com.ichi2.anki.common.permissions.hasAllPermissions
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.AnkiDroidFolder
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.common.storage.StorageDecision
import com.ichi2.anki.common.storage.isLegacyStorage
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.exception.SystemStorageException
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.setPreferencesUpToDate
import com.ichi2.anki.startup.FatalInitializationError
import com.ichi2.anki.startup.StoragePolicy
import com.ichi2.anki.startup.folderToPersist
import com.ichi2.anki.startup.maybeSetDefaultCollectionPath
import com.ichi2.anki.ui.windows.permissions.LegacyNotificationsPermissionFragment
import com.ichi2.anki.ui.windows.permissions.NotificationsPermissionFragment
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.anki.ui.windows.permissions.PermissionsFragment
import com.ichi2.anki.ui.windows.permissions.PermissionsStartingAt30Fragment
import com.ichi2.anki.ui.windows.permissions.PermissionsUntil29Fragment
import com.ichi2.utils.Permissions
import com.ichi2.utils.VersionUtils.pkgVersionName
import kotlinx.parcelize.Parcelize
import net.ankiweb.rsdroid.BackendException
import timber.log.Timber

/** Utilities for launching the first activity (currently the DeckPicker)  */
object InitialActivity {
    /**
     * Persists the collection path chosen by the required [StoragePermissionSet]'s
     * [StoragePolicy] if the user has not decided yet; no-op otherwise.
     *
     * Safe to call without permissions: a policy deferring to the user leaves the
     * [StorageDecision] undecided. Startup calls this once permissions are granted, so a
     * deferred decision is recorded before the collection is opened.
     *
     * @see maybeSetDefaultCollectionPath
     */
    fun decideStorageIfUndecided(context: Context) {
        try {
            maybeSetDefaultCollectionPath(context)
        } catch (e: SystemStorageException) {
            // startup continues: getStartupFailureType reports the undecided state
            Timber.w(e, "unable to choose a default collection path")
        }
    }

    @CheckResult
    fun getStartupFailureType(context: Context): StartupFailure? =
        getStartupFailureType(context.sharedPrefs()) { CollectionHelper.isCurrentAnkiDroidDirAccessible(context) }

    /** Returns null on success  */
    @CheckResult
    fun getStartupFailureType(
        preferences: SharedPreferences,
        initializeAnkiDroidDirectory: () -> Boolean,
    ): StartupFailure? {
        AnkiDroidApp.startupErrors.fatalError?.let {
            return StartupFailure.InitializationError(it)
        }

        // Opening the collection would throw a StorageNotConfiguredException, which is not worth
        // a crash report
        if (CollectionHelper.storageDecision(preferences) != StorageDecision.Decided) {
            Timber.i("storage location is undecided")
            return StartupFailure.StorageUndecided
        }

        val failure =
            try {
                CollectionManager.getColUnsafe()
                return null
            } catch (e: CollectionLockedException) {
                Timber.w(e)
                StartupFailure.DatabaseLocked
            } catch (e: BackendException.BackendDbException.BackendDbFileTooNewException) {
                Timber.w(e)
                StartupFailure.FutureAnkidroidVersion
            } catch (e: BackendException.BackendDbException.BackendDbFullException) {
                Timber.w(e)
                StartupFailure.DiskFull
            } catch (e: BackendException.BackendDbException.BackendDbCorruptException) {
                Timber.w(e)
                DatabaseCorruption.isDetected = true
                StartupFailure.DBError(e)
            } catch (e: SQLiteFullException) {
                // TODO: delete the android.database branches when the backend is no longer Android
                Timber.w(e)
                StartupFailure.DiskFull
            } catch (e: SQLiteDatabaseCorruptException) {
                Timber.w(e)
                DatabaseCorruption.isDetected = true
                StartupFailure.DBError(e)
            } catch (e: StorageAccessException) {
                // Same handling as the fall through, but without the exception report
                // These are now handled with a dialog and don't generate actionable reports
                Timber.w(e)
                StartupFailure.DBError(e)
            } catch (e: Exception) {
                Timber.w(e)
                CrashReportService.sendExceptionReport(e, "InitialActivity::getStartupFailureType")
                StartupFailure.DBError(e)
            }

        if (!SdCard.isMounted) {
            return StartupFailure.SDCardNotMounted
        } else if (!initializeAnkiDroidDirectory()) {
            return StartupFailure.DirectoryNotAccessible
        }

        return failure
    }

    /** @return Whether any preferences were upgraded
     */
    fun upgradePreferences(
        context: Context,
        previousVersionCode: Long,
    ): Boolean = PreferenceUpgradeService.upgradePreferences(context, previousVersionCode)

    /**
     * @return Whether a fresh install occurred and a "fresh install" setup for preferences was performed
     * This only refers to a fresh install from the preferences perspective, not from the Anki data perspective.
     *
     * NOTE: A user can wipe app data, which will mean this returns true WITHOUT deleting their collection.
     * The above note will need to be reevaluated after scoped storage migration takes place
     *
     *
     * On the other hand, restoring an app backup can cause this to return true before the Anki collection is created
     * in practice, this doesn't occur due to CollectionHelper.getCol creating a new collection, and it's called before
     * this in the startup script
     */
    @CheckResult
    fun performSetupFromFreshInstallOrClearedPreferences(preferences: SharedPreferences): Boolean {
        if (!wasFreshInstall(preferences)) {
            Timber.d("Not a fresh install [preferences]")
            return false
        }
        Timber.i("Fresh install")
        setPreferencesUpToDate(preferences)
        setUpgradedToLatestVersion(preferences)
        return true
    }

    /**
     * true if the app was launched the first time
     * false if the app was launched for the second time after a successful initialisation
     * false if the app was launched after an update
     */
    fun wasFreshInstall(preferences: SharedPreferences) = "" == preferences.getString("lastVersion", "")

    /** Sets the preference stating that the latest version has been applied  */
    fun setUpgradedToLatestVersion(preferences: SharedPreferences) {
        Timber.i("Marked prefs as upgraded to latest version: %s", pkgVersionName)
        preferences.edit { putString("lastVersion", pkgVersionName) }
    }

    /** @return false: The app has been upgraded since the last launch OR the app was launched for the first time.
     * Implementation detail:
     * This is not called in the case of performSetupFromFreshInstall returning true.
     * So this should not use the default value
     */
    fun isLatestVersion(preferences: SharedPreferences): Boolean = preferences.getString("lastVersion", "") == pkgVersionName

    sealed class StartupFailure {
        data object SDCardNotMounted : StartupFailure()

        data object DirectoryNotAccessible : StartupFailure()

        data object FutureAnkidroidVersion : StartupFailure()

        class DBError(
            val exception: Exception,
        ) : StartupFailure()

        data object DatabaseLocked : StartupFailure()

        /**
         * [AnkiDroidApp] encountered a fatal error
         */
        data class InitializationError(
            val error: FatalInitializationError,
        ) : StartupFailure() {
            val infoLink
                get() = error.infoLink

            fun toHumanReadableString(context: Context): String =
                when (error) {
                    is FatalInitializationError.WebViewError ->
                        context.getString(
                            CommonString.ankidroid_init_failed_webview,
                            error.errorDetail,
                        )
                    is FatalInitializationError.StorageError ->
                        context.getString(
                            CommonString.ankidroid_init_failed_storage,
                            error.errorDetail,
                        )
                }
        }

        data object DiskFull : StartupFailure()

        /**
         * The user has not yet decided where the collection is stored.
         *
         * @see CollectionHelper.storageDecision
         */
        data object StorageUndecided : StartupFailure()
    }
}

/**
 * Permissions requested together on a [PermissionsFragment].
 *
 * @see StoragePermissionSet
 * @see OptionalPermissionSet
 */
sealed interface PermissionSet : Parcelable {
    val permissions: List<String>

    /** The screen requesting [permissions], or `null` if there are none to request */
    val permissionsFragment: Class<out PermissionsFragment>?

    fun hasRequiredPermissions(context: Context): Boolean = hasAllPermissions(context, permissions)
}

/**
 * The permissions required to access the folder where AnkiDroid data is saved.
 *
 * See [selectStoragePermissions].
 */
@Parcelize
enum class StoragePermissionSet(
    override val permissions: List<String>,
    override val permissionsFragment: Class<out PermissionsFragment>?,
) : PermissionSet {
    LEGACY_ACCESS(Permissions.legacyStorageAccessStartupPermissions, PermissionsUntil29Fragment::class.java),

    @RequiresApi(Build.VERSION_CODES.R)
    EXTERNAL_MANAGER(Permissions.externalManagerStorageAccessStartupPermissions, PermissionsStartingAt30Fragment::class.java),

    /** The app's own folder needs no permission */
    APP_PRIVATE(permissions = emptyList(), permissionsFragment = null),
    ;

    /** Who chooses the collection folder on a fresh install. */
    @get:SuppressLint("NewApi") // a branch on EXTERNAL_MANAGER does not use its APIs
    val storagePolicy: StoragePolicy
        get() =
            when (this) {
                LEGACY_ACCESS -> StoragePolicy.Fixed(AnkiDroidFolder.PUBLIC)
                EXTERNAL_MANAGER -> StoragePolicy.UserChoosesOnPermissionScreen
                APP_PRIVATE -> StoragePolicy.Fixed(AnkiDroidFolder.APP_PRIVATE)
            }

    /** The folder to persist on startup, or `null` if the user has yet to choose. */
    fun folderToPersist(context: Context): AnkiDroidFolder? = storagePolicy.folderToPersist(hasRequiredPermissions(context))
}

/**
 * Permissions a feature requests when it is first used. AnkiDroid runs without them.
 *
 * Optional: requested on a [PermissionsBottomSheet].
 */
@Parcelize
enum class OptionalPermissionSet(
    override val permissions: List<String>,
    override val permissionsFragment: Class<out PermissionsFragment>,
) : PermissionSet {
    /**
     * For devices with API >= 33.
     * @see NotificationsPermissionFragment
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    NOTIFICATIONS(listOf(Permissions.notificationsPermission), NotificationsPermissionFragment::class.java),

    /**
     * For devices with API < 33.
     * There is no formal permission to request, but it is possible for the user to manually disable notifications in Settings.
     * If they want notifications, we need to direct the user to the OS settings via the [LegacyNotificationsPermissionFragment] so they can re-enable them.
     */
    LEGACY_NOTIFICATIONS(listOf(LEGACY_POST_NOTIFICATIONS), LegacyNotificationsPermissionFragment::class.java),
}

/**
 * Returns the [StoragePermissionSet] required to access the folder where AnkiDroid data is saved.
 * [AnkiDroidFolder.PUBLIC] is preferred, as it reduces risk of data loss.
 * When impossible, we use the app-private directory.
 * See https://github.com/ankidroid/Anki-Android/issues/5304 for more context.
 */
internal fun selectStoragePermissions(
    canManageExternalStorage: Boolean,
    currentFolderIsAccessibleAndLegacy: Boolean,
): StoragePermissionSet {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || currentFolderIsAccessibleAndLegacy) {
        // match AnkiDroid behaviour before scoped storage - force the use of ~/AnkiDroid,
        // since it's fast & safe up to & including 'Q'
        // If a user upgrades their OS from Android 10 to 11 then storage speed is severely reduced
        // and a user should use one of the below options to provide faster speeds
        return StoragePermissionSet.LEGACY_ACCESS
    }

    // If the user can manage external storage, we can access the safe folder & access is fast
    return if (canManageExternalStorage) {
        StoragePermissionSet.EXTERNAL_MANAGER
    } else {
        StoragePermissionSet.APP_PRIVATE
    }
}

fun selectStoragePermissions(context: Context): StoragePermissionSet {
    // `false`: the collection is app-private, so it can be accessed without storage permissions
    // `null`: no collection path is set
    val currentFolderIsLegacy = isLegacyStorage(context, setCollectionPath = false)
    if (currentFolderIsLegacy == false) {
        return StoragePermissionSet.APP_PRIVATE
    }

    val canAccessLegacyStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Environment.isExternalStorageLegacy()
    val currentFolderIsAccessibleAndLegacy = canAccessLegacyStorage && currentFolderIsLegacy == true

    return selectStoragePermissions(
        canManageExternalStorage = Permissions.canManageExternalStorage(context),
        currentFolderIsAccessibleAndLegacy = currentFolderIsAccessibleAndLegacy,
    )
}

/** The folder where AnkiDroid data is saved. See [selectStoragePermissions]. */
fun selectAnkiDroidFolder(context: Context): AnkiDroidFolder =
    if (selectStoragePermissions(context) == StoragePermissionSet.APP_PRIVATE) {
        AnkiDroidFolder.APP_PRIVATE
    } else {
        AnkiDroidFolder.PUBLIC
    }

/**
 * Configures either hardware or software rendering
 */
fun configureRenderingMode() {
    val preferences = AnkiDroidApp.sharedPrefs()
    // For Android 8/8.1 we want to use software rendering by default or the Reviewer UI is broken #7369
    if (sdkVersion != Build.VERSION_CODES.O && sdkVersion != Build.VERSION_CODES.O_MR1) return
    if (!preferences.contains("softwareRender")) {
        Timber.i("Android 8/8.1 detected with no render preference. Turning on software render.")
        preferences.edit { putBoolean("softwareRender", true) }
    } else {
        Timber.i("Android 8/8.1 detected, software render preference already exists.")
    }
}
