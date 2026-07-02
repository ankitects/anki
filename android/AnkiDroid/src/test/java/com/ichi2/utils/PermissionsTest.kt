/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.utils

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.utils.Permissions.requestPermissionThroughDialogOrSettings
import com.ichi2.utils.Permissions.showToastAndOpenAppSettingsScreen
import io.mockk.every
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
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
class PermissionsTest {
    private lateinit var activity: Activity
    private lateinit var context: Context
    private lateinit var fragment: Fragment
    private lateinit var fragmentManager: FragmentManager
    private lateinit var permissionRequestLauncher: ActivityResultLauncher<String>
    private lateinit var permissionsSpy: Permissions

    @Before
    fun setUp() {
        context = getApplicationContext()
        activity = mockk(relaxed = true)
        fragment = mockk(relaxed = true)
        fragmentManager = mockk(relaxed = true)
        permissionRequestLauncher = mockk(relaxed = true)
        permissionsSpy = spyk(Permissions)

        // No need to test the behaviour of the shouldShowRequestPermissionRationale system API,
        // as that's not our code, so we just mock its behaviour
        mockkStatic(ActivityCompat::class)
        // Similarly with ContextCompat.checkSelfPermission
        mockkStatic(ContextCompat::class)

        Prefs.notificationsPermissionRequested = false
    }

    @After
    fun tearDown() {
        unmockkAll()
        Prefs.notificationsPermissionRequested = false
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
    fun `requestPermissionThroughDialogOrSettings requests permission if we can request it`() {
        Prefs.notificationsPermissionRequested = true // not first call
        setCanPermissionBeRequested(true)
        triggerPermissionRequest()
        verifyPermissionWasRequested()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU - 1])
    fun `showNotificationsPermissionBottomSheetIfNeeded does nothing below API 33`() {
        setPermissionsGranted(false)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldFail()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    fun `showNotificationsPermissionBottomSheetIfNeeded does nothing if permission is granted`() {
        setPermissionsGranted(true)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldFail()
    }

    @Test
    @Config(sdk = [Build.VERSION_CODES.TIRAMISU])
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.TIRAMISU)
    fun `showNotificationsPermissionBottomSheetIfNeeded works on first call and afterward only if system allows it`() {
        mockkObject(PermissionsBottomSheet)

        setPermissionsGranted(false)
        setCanPermissionBeRequested(true)
        showBottomSheetShouldSucceed()
        showBottomSheetShouldSucceed()
        verify(exactly = 2) { PermissionsBottomSheet.launch(fragmentManager, PermissionSet.NOTIFICATIONS) }

        setCanPermissionBeRequested(false)
        showBottomSheetShouldFail()
        verify(exactly = 2) { PermissionsBottomSheet.launch(fragmentManager, PermissionSet.NOTIFICATIONS) }
    }

    private fun setPermissionsGranted(granted: Boolean) {
        val permissionStatus = if (granted) PackageManager.PERMISSION_GRANTED else PackageManager.PERMISSION_DENIED
        every { ContextCompat.checkSelfPermission(any(), any()) } returns permissionStatus
    }

    private fun setCanPermissionBeRequested(canBeRequested: Boolean) {
        every { ActivityCompat.shouldShowRequestPermissionRationale(any(), any()) } returns canBeRequested
    }

    private fun triggerPermissionRequest() {
        fragment.requestPermissionThroughDialogOrSettings(
            activity,
            DUMMY_PERMISSION_STRING,
            Prefs::notificationsPermissionRequested,
            permissionRequestLauncher,
        )
    }

    private fun verifyPermissionWasRequested() {
        assertThat("requested flag should always be true after requesting", Prefs.notificationsPermissionRequested, equalTo(true))
        verify(exactly = 1) { permissionRequestLauncher.launch(DUMMY_PERMISSION_STRING) }
        verify(exactly = 0) { fragment.showToastAndOpenAppSettingsScreen(any<Int>()) }
    }

    private fun verifyOSSettingsWasOpened() {
        assertThat("requested flag should always be true after requesting", Prefs.notificationsPermissionRequested, equalTo(true))
        verify(exactly = 0) { permissionRequestLauncher.launch(DUMMY_PERMISSION_STRING) }

        val intentSlot = slot<Intent>()
        verify(exactly = 1) { fragment.startActivity(capture(intentSlot)) }
        assertThat(intentSlot.captured.action, equalTo(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
    }

    private fun showBottomSheetShouldFail() {
        permissionsSpy.showNotificationsPermissionBottomSheetIfNeeded(activity, fragmentManager) {
            throw IllegalStateException("callback should not be called")
        }
    }

    private fun showBottomSheetShouldSucceed() {
        permissionsSpy.showNotificationsPermissionBottomSheetIfNeeded(activity, fragmentManager) {
            Prefs.notificationsPermissionRequested = true
        }
    }

    companion object {
        private const val DUMMY_PERMISSION_STRING = "DUMMY_PERMISSION_STRING"
    }
}
