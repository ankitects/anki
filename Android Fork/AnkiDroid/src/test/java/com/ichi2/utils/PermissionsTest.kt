// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.OptionalPermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.common.permissions.LEGACY_POST_NOTIFICATIONS
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.utils.Permissions.attemptToEnableNotifications
import com.ichi2.utils.Permissions.openAppSettingsScreen
import com.ichi2.utils.Permissions.openAppSettingsScreenForPermission
import com.ichi2.utils.Permissions.requestPermissionThroughDialogOrSettings
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.reflect.KMutableProperty0

@RunWith(AndroidJUnit4::class)
class PermissionsTest {
    private lateinit var fragmentActivity: FragmentActivity
    private lateinit var context: Context
    private lateinit var fragment: Fragment
    private lateinit var fragmentManager: FragmentManager
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>
    private lateinit var permissionsSpy: Permissions

    @Before
    fun setUp() {
        context = getApplicationContext()
        fragmentActivity = mockk(relaxed = true)
        fragment = mockk(relaxed = true)
        every { fragment.requireContext() } returns context
        every { fragment.requireActivity() } returns fragmentActivity
        fragmentManager = mockk(relaxed = true)
        permissionRequestLauncher = mockk(relaxed = true)
        permissionsSpy = spyk(Permissions)

        // No need to test the behaviour of the shouldShowRequestPermissionRationale system API,
        // as that's not our code, so we just mock its behaviour
        mockkStatic(ActivityCompat::class)
        // Similarly with NotificationManagerCompat.areNotificationsEnabled
        mockkStatic(NotificationManagerCompat::class)

        Prefs.notificationsPermissionRequested = false
        Prefs.notificationsBottomSheetShownBelowAPI33 = false
    }

    @After
    fun tearDown() {
        unmockkAll()
        Prefs.notificationsPermissionRequested = false
        Prefs.notificationsBottomSheetShownBelowAPI33 = false
    }

    @Test
    fun `openAppSettingsScreen is a no-op if no app can show the app settings screen`() {
        Robolectric.buildActivity(Activity::class.java).use { controller ->
            val realActivity = controller.setup().get()
            // throw when resolving `ACTION_APPLICATION_DETAILS_SETTINGS`
            shadowOf(realActivity.application).checkActivities(true)
            assertDoesNotThrow { realActivity.openAppSettingsScreen() }
        }
    }

    @Test
    fun `requestPermissionThroughDialogOrSettings requests permission on first call`() {
        Prefs.notificationsPermissionRequested = false
        triggerPermissionRequest()
        verifyPermissionWasRequested()
    }

    @Test
    fun `requestPermissionThroughDialogOrSettings opens settings screen if we can't request the permission`() {
        Prefs.notificationsPermissionRequested = true // not first call
        setCanPermissionBeRequested(false)
        triggerPermissionRequest()
        verifyOSSettingsWasOpened()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `requestPermissionThroughDialogOrSettings for notif permission opens notif settings screen if we can't request the permission`() {
        Prefs.notificationsPermissionRequested = true // not first call
        setCanPermissionBeRequested(false)
        triggerPermissionRequest(permission = Permissions.notificationsPermission)
        verifyOSSettingsWasOpened(openedActivityAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    fun `requestPermissionThroughDialogOrSettings requests permission on subsequent calls if we can request it`() {
        Prefs.notificationsPermissionRequested = true // not first call
        setCanPermissionBeRequested(true)
        triggerPermissionRequest()
        verifyPermissionWasRequested()
    }

    @Test
    fun `canPermissionBeRequested reads the flag without kotlin-reflect`() {
        val flag = mockk<KMutableProperty0<Boolean>> { every { get() } returns false }

        val canRequest = Permissions.canPermissionBeRequested(fragmentActivity, DUMMY_PERMISSION_STRING, flag)

        assertThat(canRequest, equalTo(true))
    }

    @Test
    fun `requestPermissionThroughDialogOrSettings sets the flag without kotlin-reflect`() {
        val flag =
            mockk<KMutableProperty0<Boolean>> {
                every { get() } returns false
                every { set(any()) } just Runs
            }

        fragment.requestPermissionThroughDialogOrSettings(fragmentActivity, DUMMY_PERMISSION_STRING, flag, permissionRequestLauncher)

        verify(exactly = 1) { flag.set(true) }
        verifyPermissionWasRequested()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `attemptToEnableNotifications for API at 33+ requests the notifications permission`() {
        Prefs.notificationsPermissionRequested = false
        fragment.attemptToEnableNotifications(permissionRequestLauncher)
        verifyPermissionWasRequested(permission = Permissions.notificationsPermission)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `attemptToEnableNotifications for API at 33+ opens notif settings screen if we can't request the permission`() {
        Prefs.notificationsPermissionRequested = true // not first call
        setCanPermissionBeRequested(false)
        fragment.attemptToEnableNotifications(permissionRequestLauncher)
        verifyOSSettingsWasOpened(openedActivityAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU - 1])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun `attemptToEnableNotifications for API before 33 opens notif settings screen without requesting a permission`() {
        fragment.attemptToEnableNotifications(permissionRequestLauncher)
        // The notifications permission does not exist below API 33, so it should never be requested
        verify(exactly = 0) { permissionRequestLauncher.launch(any()) }
        // The OS request dialog should not have been opened
        assertThat(Prefs.notificationsPermissionRequested, equalTo(false))
        verifyOSSettingsWasOpened(openedActivityAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU, Build.VERSION_CODES.TIRAMISU - 1])
    fun `showNotificationsPermissionBottomSheetIfNeeded does nothing if permission is granted`() {
        setPermissionsGranted(true)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldFail()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `showNotificationsPermissionBottomSheetIfNeeded for API at 33+ works on first call and afterward only if system allows it`() {
        mockkObject(PermissionsBottomSheet)

        setPermissionsGranted(false)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldSucceed()
        showBottomSheetShouldSucceed()
        verify(exactly = 2) { PermissionsBottomSheet.launch(fragmentManager, OptionalPermissionSet.NOTIFICATIONS) }

        setCanPermissionBeRequested(false)
        showBottomSheetShouldFail()
        verify(exactly = 2) { PermissionsBottomSheet.launch(fragmentManager, OptionalPermissionSet.NOTIFICATIONS) }
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU - 1])
    fun `showNotificationsPermissionBottomSheetIfNeeded for API before 33 only works on first call if cannot send notifications`() {
        mockkObject(PermissionsBottomSheet)

        setPermissionsGranted(false)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldSucceed()
        assertThat(Prefs.notificationsBottomSheetShownBelowAPI33, equalTo(true))
        showBottomSheetShouldFail()

        setCanPermissionBeRequested(false)
        showBottomSheetShouldFail()
        verify(exactly = 1) { PermissionsBottomSheet.launch(fragmentManager, OptionalPermissionSet.LEGACY_NOTIFICATIONS) }
        assertThat(Prefs.notificationsBottomSheetShownBelowAPI33, equalTo(true))
    }

    @Test
    fun `openAppSettingsScreenForPermission opens the generic app settings screen for a null permission`() {
        fragment.openAppSettingsScreenForPermission(null)
        verifyOSSettingsWasOpened()
    }

    @Test
    fun `openAppSettingsScreenForPermission opens the generic app settings screen for an unhandled permission`() {
        fragment.openAppSettingsScreenForPermission(DUMMY_PERMISSION_STRING)
        verifyOSSettingsWasOpened()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `openAppSettingsScreenForPermission for the notifications permission at API 33+ opens the notif settings screen`() {
        fragment.openAppSettingsScreenForPermission(Permissions.notificationsPermission)
        verifyOSSettingsWasOpened(openedActivityAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU, Build.VERSION_CODES.TIRAMISU - 1])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
    fun `openAppSettingsScreenForPermission for the legacy notifications sentinel opens the notif settings screen`() {
        fragment.openAppSettingsScreenForPermission(LEGACY_POST_NOTIFICATIONS)
        verifyOSSettingsWasOpened(openedActivityAction = Settings.ACTION_APP_NOTIFICATION_SETTINGS)
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.O - 1])
    fun `openAppSettingsScreenForPermission for the legacy notifications sentinel before API 26 opens the generic app settings screen`() {
        fragment.openAppSettingsScreenForPermission(LEGACY_POST_NOTIFICATIONS)
        verifyOSSettingsWasOpened()
    }

    @Test
    fun `legacy_post_notification_permission resource matches the LEGACY_POST_NOTIFICATIONS constant`() {
        assertThat(
            context.getString(R.string.legacy_post_notification_permission),
            equalTo(LEGACY_POST_NOTIFICATIONS),
        )
    }

    private fun setPermissionsGranted(granted: Boolean) {
        every { NotificationManagerCompat.from(any()).areNotificationsEnabled() } returns granted
    }

    private fun setCanPermissionBeRequested(canBeRequested: Boolean) {
        every { ActivityCompat.shouldShowRequestPermissionRationale(any(), any()) } returns canBeRequested
    }

    private fun triggerPermissionRequest(permission: String = DUMMY_PERMISSION_STRING) {
        fragment.requestPermissionThroughDialogOrSettings(
            fragmentActivity,
            permission,
            Prefs::notificationsPermissionRequested,
            permissionRequestLauncher,
        )
        assertThat("requested flag should always be true after requesting", Prefs.notificationsPermissionRequested, equalTo(true))
    }

    private fun verifyPermissionWasRequested(permission: String = DUMMY_PERMISSION_STRING) {
        verify(exactly = 1) { permissionRequestLauncher.launch(permission) }
        // No settings screen should be opened if the permission could be requested directly
        verify(exactly = 0) { fragmentActivity.startActivity(any()) }
    }

    private fun verifyOSSettingsWasOpened(openedActivityAction: String = Settings.ACTION_APPLICATION_DETAILS_SETTINGS) {
        verify(exactly = 0) { permissionRequestLauncher.launch(any()) }

        val intentSlot = slot<Intent>()
        verify(exactly = 1) { fragmentActivity.startActivity(capture(intentSlot)) }
        assertThat(intentSlot.captured.action, equalTo(openedActivityAction))
    }

    private fun showBottomSheetShouldFail() {
        permissionsSpy.showNotificationsPermissionBottomSheetIfNeeded(fragmentActivity, fragmentManager) {
            throw IllegalStateException("callback should not be called")
        }
    }

    private fun showBottomSheetShouldSucceed() {
        permissionsSpy.showNotificationsPermissionBottomSheetIfNeeded(fragmentActivity, fragmentManager) {
            Prefs.notificationsPermissionRequested = true
        }
    }

    companion object {
        private const val DUMMY_PERMISSION_STRING = "DUMMY_PERMISSION_STRING"
    }
}
