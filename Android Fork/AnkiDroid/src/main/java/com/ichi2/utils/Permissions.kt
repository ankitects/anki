// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.OptionalPermissionSet
import com.ichi2.anki.common.permissions.LEGACY_POST_NOTIFICATIONS
import com.ichi2.anki.common.permissions.MANAGE_EXTERNAL_STORAGE
import com.ichi2.anki.common.permissions.canPostNotifications
import com.ichi2.anki.common.permissions.hasPermission
import com.ichi2.anki.common.utils.android.isRobolectric
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.compat.CompatHelper.Companion.getPackageInfoCompat
import com.ichi2.anki.compat.GET_PERMISSIONS_L
import com.ichi2.anki.compat.PackageInfoFlagsCompat
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.utils.Permissions.arePermissionsDefinedInManifest
import timber.log.Timber
import kotlin.reflect.KMutableProperty0

object Permissions {
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    val notificationsPermission: String = Manifest.permission.POST_NOTIFICATIONS

    /**
     * Returns whether AnkiDroid is able to request a permission from the user.
     *
     * @param activity Required by [ActivityCompat.shouldShowRequestPermissionRationale] to check if
     * the user has clicked "Don't ask again" on previous requests.
     * @param permission The permission to be requested.
     * @param permissionRequestedFlag A reference to a mutable boolean flag that indicates whether the permission
     * has been requested before. Required because [ActivityCompat.shouldShowRequestPermissionRationale]
     * only works correctly after the first request.
     */
    fun canPermissionBeRequested(
        activity: Activity,
        permission: String,
        permissionRequestedFlag: KMutableProperty0<Boolean>,
    ): Boolean =
        if (permissionRequestedFlag.get()) {
            // Previous requests have been made, only request again if we are not blocked from requesting
            ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
        } else {
            // No previous requests, so we can request
            true
        }

    /**
     * Requests a permission through a system dialog if the user has never been asked for this permission before or if they haven't
     * clicked "Don't ask again" on previous requests (implicitly triggered if the user presses "deny" multiple times in newer API levels).
     * If the user has clicked "Don't ask again" in the past, meaning Android won't let us open the permission request
     * system UI dialog anymore, we assume that by triggering this method that the user has expressed a clear intent to grant the permission,
     * so we open the app settings screen to let them manually grant it.
     *
     * @param activity Used for checking whether the user is open to granting the permission.
     * @param permission The permission to be requested.
     * @param permissionRequestedFlag A reference to a mutable boolean flag that indicates whether the permission
     * has been requested before. Required because [ActivityCompat.shouldShowRequestPermissionRationale]
     * only works correctly after the first request.
     * @param permissionRequestLauncher The [ActivityResultLauncher] used to request the permission.
     */
    fun Fragment.requestPermissionThroughDialogOrSettings(
        activity: Activity,
        permission: String,
        permissionRequestedFlag: KMutableProperty0<Boolean>,
        permissionRequestLauncher: ActivityResultLauncher<String>,
    ) {
        if (canPermissionBeRequested(activity, permission, permissionRequestedFlag)) {
            permissionRequestedFlag.set(true)
            permissionRequestLauncher.launch(permission)
        } else {
            showToastAndOpenAppSettingsScreenForPermission(permission, CommonString.manually_grant_permissions)
        }
    }

    /**
     * At and above API 33, the formal notification permission exists and can be requested via the usual permission flow.
     * Below API 33, the permission is implicitly granted, but the user can still disable notifications for the app in system settings.
     * In that case, we open the system settings screen for the user to manually enable notifications.
     */
    fun Fragment.attemptToEnableNotifications(notificationPermissionLauncher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionThroughDialogOrSettings(
                activity = requireActivity(),
                permission = notificationsPermission,
                permissionRequestedFlag = Prefs::notificationsPermissionRequested,
                permissionRequestLauncher = notificationPermissionLauncher,
            )
        } else {
            showToastAndOpenAppSettingsScreenForPermission(LEGACY_POST_NOTIFICATIONS, CommonString.manually_grant_permissions)
        }
    }

    /**
     * Shows the [com.ichi2.anki.ui.windows.permissions.NotificationsPermissionFragment] in the [PermissionsBottomSheet]
     * if notification permissions have not been granted.
     *
     * Does nothing if the permission has already been granted.
     * Always shows the bottom sheet the first time it detects that the permission has not been granted.
     * Does nothing on API 33+ if the user has previously denied the permission and selected "Don't ask again".
     * Does nothing on API <33 if the user has seen the bottom sheet once before.
     *
     * Even though the explicit permission is exclusive to API 33+, this method is still useful for API <33 because the user can
     * manually disable notifications for the app in system settings.
     *
     * @param activity Used for checking whether notification permissions have been granted, or if the user has clicked
     * "Don't ask again" on previous requests.
     * @param fragmentManager Used for launching the BottomSheet, if necessary.
     * @param callback Executed only if the BottomSheet is actually shown.
     */
    fun showNotificationsPermissionBottomSheetIfNeeded(
        activity: Activity,
        fragmentManager: FragmentManager,
        callback: () -> Unit,
    ) {
        if (canPostNotifications(context = activity)) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val canRequest =
                canPermissionBeRequested(
                    activity,
                    notificationsPermission,
                    permissionRequestedFlag = Prefs::notificationsPermissionRequested,
                )
            if (!canRequest) {
                Timber.i("Not showing notifications permissions bottom sheet: permission permanently denied")
                return
            }
            Timber.i("Showing notifications permissions bottom sheet: API >= 33")
            PermissionsBottomSheet.launch(fragmentManager, OptionalPermissionSet.NOTIFICATIONS)
        } else {
            if (Prefs.notificationsBottomSheetShownBelowAPI33) {
                Timber.i("Not showing notifications permissions bottom sheet: already attempted")
                return
            }
            Timber.i("Showing notifications permissions bottom sheet: API < 33")
            PermissionsBottomSheet.launch(fragmentManager, OptionalPermissionSet.LEGACY_NOTIFICATIONS)
            Prefs.notificationsBottomSheetShownBelowAPI33 = true
        }

        callback()
    }

    /**
     * Request notification permissions from the user if they have not been requested due to syncing ever before.
     * Should be called whenever the user logs in to an account, but not right before the login activity is about
     * to be dismissed, as that will cause the BottomSheet to show up and then immediately be dismissed.
     * This constraint means the code needs to be called in multiple places (DeckPicker, LoginFragment, etc.),
     * and hence it is placed here as a utility function.
     */
    fun requestNotificationPermissionsForSyncing(fragmentActivity: FragmentActivity) {
        if (!Prefs.syncNotifsRequestShown) {
            showNotificationsPermissionBottomSheetIfNeeded(fragmentActivity, fragmentActivity.supportFragmentManager) {
                Prefs.syncNotifsRequestShown = true
            }
        }
    }

    val legacyStorageAccessStartupPermissions =
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )

    @RequiresApi(Build.VERSION_CODES.R)
    val externalManagerStorageAccessStartupPermissions =
        listOf(
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
        )

    const val RECORD_AUDIO_PERMISSION = Manifest.permission.RECORD_AUDIO

    fun canRecordAudio(context: Context): Boolean = hasPermission(context, RECORD_AUDIO_PERMISSION)

    /**
     * Detects if permissions are defined via <uses-permission> in the Manifest.
     * This does **not** mean the permission has been granted.
     * Intention is to be used when a permissions may be changed by build flavours
     *
     * Example:
     * * Play => no 'manage external storage'
     *
     * @param permissions One or more permission strings, typically defined in [Manifest.permission]
     * @return `true` if all permissions were granted. `false` otherwise, or if an error occurs.
     */
    fun Context.arePermissionsDefinedInManifest(
        packageName: String,
        vararg permissions: String,
    ): Boolean {
        try {
            val permissionsInManifest = getPermissionsDefinedInManifest(packageName) ?: return false
            return permissions.all { permissionsInManifest.contains(it) }
        } catch (e: Exception) {
            Timber.w(e)
        }
        return false
    }

    private fun Context.getPermissionsDefinedInManifest(packageName: String): Array<out String>? =
        try {
            // requestedPermissions => <uses-permission> in manifest
            val flags = PackageInfoFlagsCompat.of(GET_PERMISSIONS_L)
            getPackageInfoCompat(packageName, flags)!!.requestedPermissions
        } catch (e: Exception) {
            Timber.w(e)
            null
        }

    /**
     * @see Context.arePermissionsDefinedInManifest
     */
    fun Context.arePermissionsDefinedInAnkiDroidManifest(vararg permissions: String) =
        this.arePermissionsDefinedInManifest(this.packageName, *permissions)

    /**
     * Whether it would be possible to manage external storage (potentially after requesting permission).
     */
    fun canManageExternalStorage(context: Context): Boolean {
        // TODO: See if we can move this to a testing manifest
        if (isRobolectric) {
            return false
        }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.R &&
            context.arePermissionsDefinedInAnkiDroidManifest(MANAGE_EXTERNAL_STORAGE)
    }

    /**
     * Whether [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS] may be used to ask the user
     * to exempt AnkiDroid from battery optimization.
     *
     * Google Play restricts [Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS].
     */
    fun canRequestIgnoreBatteryOptimizations(context: Context): Boolean =
        context.arePermissionsDefinedInAnkiDroidManifest(Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)

    /**
     * Opens the Android settings for AnkiDroid if the device provide this feature.
     * Lets a user grant any missing permissions which have been permanently denied.
     */
    fun Activity.openAppSettingsScreen() {
        Timber.i("launching ACTION_APPLICATION_DETAILS_SETTINGS")
        try {
            startActivity(
                Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", packageName, null),
                ),
            )
        } catch (e: ActivityNotFoundException) {
            Timber.w(e, "No app can show the app settings screen")
            showThemedToast(this, CommonString.activity_start_failed, false)
        }
    }

    /**
     * Opens the Android settings for AnkiDroid if the device provide this feature.
     * Lets a user grant any missing permissions which have been permanently denied.
     */
    fun Fragment.openAppSettingsScreen() = requireActivity().openAppSettingsScreen()

    /**
     * Opens the Android notifications settings for AnkiDroid if the device provides this feature.
     * Otherwise, falls back to opening the generic app settings screen.
     *
     * @param highlightedChannel The notification channel to highlight in the notifications settings screen,
     * to draw the user's attention.
     */
    fun Activity.openAppNotificationsSettingsScreen(highlightedChannel: NotificationChannel? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Timber.i("launching ACTION_APP_NOTIFICATION_SETTINGS")
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                    highlightedChannel?.let { putExtra(Settings.EXTRA_CHANNEL_ID, it.id) }
                },
            )
        } else {
            openAppSettingsScreen()
        }
    }

    fun Fragment.openAppNotificationsSettingsScreen(highlightedChannel: NotificationChannel? = null) =
        requireActivity().openAppNotificationsSettingsScreen(highlightedChannel)

    /**
     * Opens the Android settings screen for a specific permission. Add more branches to the `when` statement as needed.
     * If no branch matches, falls back to opening the generic app settings screen.
     *
     * @param permission The permission to open the settings screen for. Can be [LEGACY_POST_NOTIFICATIONS] for pre-API 33 notifications.
     * Can be null to open the generic app settings screen.
     */
    fun Fragment.openAppSettingsScreenForPermission(permission: String?) {
        when {
            ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) && (permission == notificationsPermission)) ||
                permission == LEGACY_POST_NOTIFICATIONS ->
                openAppNotificationsSettingsScreen()

            // Else, default to opening the root page of the app settings screen
            else -> openAppSettingsScreen()
        }
    }

    fun Fragment.showToastAndOpenAppSettingsScreenForPermission(
        permission: String?,
        @StringRes message: Int,
    ) {
        showThemedToast(requireContext(), message, false)
        openAppSettingsScreenForPermission(permission)
    }

    fun Fragment.showToastAndOpenAppSettingsScreenForPermission(
        permission: String?,
        message: String,
    ) {
        showThemedToast(requireContext(), message, false)
        openAppSettingsScreenForPermission(permission)
    }
}
