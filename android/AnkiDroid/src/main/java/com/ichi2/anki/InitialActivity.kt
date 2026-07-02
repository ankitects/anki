/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki

import android.Manifest
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
import com.ichi2.anki.common.permissions.hasAllPermissions
import com.ichi2.anki.common.utils.android.SdCard
import com.ichi2.anki.compat.CompatHelper.Companion.sdkVersion
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.servicelayer.PreferenceUpgradeService
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.setPreferencesUpToDate
import com.ichi2.anki.servicelayer.ScopedStorageService.isLegacyStorage
import com.ichi2.anki.storage.AnkiDroidFolder
import com.ichi2.anki.ui.windows.permissions.InternetPermissionFragment
import com.ichi2.anki.ui.windows.permissions.NotificationsPermissionFragment
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
    @CheckResult
    fun getStartupFailureType(context: Context): StartupFailure? =
        getStartupFailureType { CollectionHelper.isCurrentAnkiDroidDirAccessible(context) }

    /** Returns null on success  */
    @CheckResult
    fun getStartupFailureType(initializeAnkiDroidDirectory: () -> Boolean): StartupFailure? {
        AnkiDroidApp.fatalError?.let {
            return StartupFailure.InitializationError(it)
        }

        val failure =
            try {
                CollectionManager.getColUnsafe()
                return null
            } catch (e: BackendException.BackendDbException.BackendDbLockedException) {
                Timber.w(e)
                StartupFailure.DatabaseLocked
            } catch (e: BackendException.BackendDbException.BackendDbFileTooNewException) {
                Timber.w(e)
                StartupFailure.FutureAnkidroidVersion
            } catch (e: SQLiteFullException) {
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
                            R.string.ankidroid_init_failed_webview,
                            error.errorDetail,
                        )
                    is FatalInitializationError.StorageError ->
                        context.getString(
                            R.string.ankidroid_init_failed_storage,
                            error.errorDetail,
                        )
                }
        }

        data object DiskFull : StartupFailure()
    }
}

@Parcelize
enum class PermissionSet(
    val permissions: List<String>,
    val permissionsFragment: Class<out PermissionsFragment>?,
) : Parcelable {
    LEGACY_ACCESS(Permissions.legacyStorageAccessStartupPermissions, PermissionsUntil29Fragment::class.java),

    @RequiresApi(Build.VERSION_CODES.R)
    EXTERNAL_MANAGER(Permissions.externalManagerStorageAccessStartupPermissions, PermissionsStartingAt30Fragment::class.java),

    APP_PRIVATE(Permissions.appPrivateStartupPermissions, InternetPermissionFragment::class.java),

    /** Optional. */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    NOTIFICATIONS(listOf(Manifest.permission.POST_NOTIFICATIONS), NotificationsPermissionFragment::class.java),
    ;

    fun hasRequiredPermissions(context: Context): Boolean = hasAllPermissions(context, permissions)
}

/**
 * Returns the [PermissionSet] required to access the folder where AnkiDroid data is saved.
 * [com.ichi2.anki.storage.AnkiDroidFolder.PUBLIC] is preferred, as it reduces risk of data loss.
 * When impossible, we use the app-private directory.
 * See https://github.com/ankidroid/Anki-Android/issues/5304 for more context.
 */
internal fun selectStoragePermissions(
    canManageExternalStorage: Boolean,
    currentFolderIsAccessibleAndLegacy: Boolean,
): PermissionSet {
    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q || currentFolderIsAccessibleAndLegacy) {
        // match AnkiDroid behaviour before scoped storage - force the use of ~/AnkiDroid,
        // since it's fast & safe up to & including 'Q'
        // If a user upgrades their OS from Android 10 to 11 then storage speed is severely reduced
        // and a user should use one of the below options to provide faster speeds
        return PermissionSet.LEGACY_ACCESS
    }

    // If the user can manage external storage, we can access the safe folder & access is fast
    return if (canManageExternalStorage) {
        PermissionSet.EXTERNAL_MANAGER
    } else {
        PermissionSet.APP_PRIVATE
    }
}

fun selectStoragePermissions(context: Context): PermissionSet {
    val canAccessLegacyStorage = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || Environment.isExternalStorageLegacy()
    val currentFolderIsAccessibleAndLegacy = canAccessLegacyStorage && isLegacyStorage(context, setCollectionPath = false) == true

    return selectStoragePermissions(
        canManageExternalStorage = Permissions.canManageExternalStorage(context),
        currentFolderIsAccessibleAndLegacy = currentFolderIsAccessibleAndLegacy,
    )
}

/** The folder where AnkiDroid data is saved. See [selectStoragePermissions]. */
fun selectAnkiDroidFolder(context: Context): AnkiDroidFolder =
    if (selectStoragePermissions(context) == PermissionSet.APP_PRIVATE) {
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
