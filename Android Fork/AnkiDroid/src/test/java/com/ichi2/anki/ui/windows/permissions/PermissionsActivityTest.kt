// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.permissions

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import androidx.core.content.edit
import androidx.fragment.app.commitNow
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ActivityScenario.ActivityAction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.OptionalPermissionSet
import com.ichi2.anki.PermissionSet
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.StoragePermissionSet
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import com.ichi2.testutils.grantPermissions
import com.ichi2.testutils.publicCollectionPath
import com.ichi2.testutils.withAllFilesAccess
import com.ichi2.testutils.withManageExternalStorageInManifest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class PermissionsActivityTest : RobolectricTest() {
    @Test
    fun testActivityCantBeClosedByBackButton() {
        testActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
            assertThat("activity is not finishing", !activity.isFinishing)
        }
    }

    @Test
    fun testOnClickingContinueActivityFinishes() {
        grantPermissions(*ARBITRARY_PERMISSION_SET.permissions.toTypedArray())
        testActivity { activity ->
            assertTrue(activity.binding.continueButton.isEnabled)
            activity.binding.continueButton.performClick()
            assertThat("activity is finishing", activity.isFinishing)
        }
    }

    @Test // Issue 13574: completing the screen must record the storage decision
    @Config(sdk = [Build.VERSION_CODES.R])
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    fun `continuing decides the collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }
        withManageExternalStorageInManifest {
            withAllFilesAccess {
                testActivity(StoragePermissionSet.EXTERNAL_MANAGER) { activity ->
                    assertTrue(activity.binding.continueButton.isEnabled)
                    activity.binding.continueButton.performClick()
                    assertThat("activity is finishing", activity.isFinishing)
                }
            }
        }
        assertEquals(publicCollectionPath, collectionPath)
    }

    @Test // Issue 13574: PermissionsStartingAt30Fragment completes the screen once 'All files access' is granted
    @Config(sdk = [Build.VERSION_CODES.R])
    @SuppressLint("NewApi") // EXTERNAL_MANAGER requires R, guaranteed by @Config
    fun `completing without Continue decides the collection path`() {
        targetContext.sharedPrefs().edit { remove(CollectionHelper.PREF_COLLECTION_PATH) }

        val settingsScreen = ComponentName("com.android.settings", "AllFilesAccessSettings")
        shadowOf(targetContext.packageManager).apply {
            addActivityIfNotPresent(settingsScreen).exported = true
            addIntentFilterForActivity(
                settingsScreen,
                IntentFilter(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    addCategory(Intent.CATEGORY_DEFAULT)
                    addDataScheme("package")
                },
            )
        }

        withManageExternalStorageInManifest {
            testActivity(StoragePermissionSet.EXTERNAL_MANAGER) { activity ->
                assertFalse(activity.binding.continueButton.isEnabled)
                activity.findViewById<PermissionsItem>(R.id.all_files_permission).performClick()

                val shadowActivity = shadowOf(activity)
                val settingsIntent = assertNotNull(shadowActivity.nextStartedActivityForResult).intent
                assertEquals(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, settingsIntent.action)
                assertNull(collectionPath)

                withAllFilesAccess {
                    shadowActivity.receiveResult(settingsIntent, Activity.RESULT_CANCELED, Intent())
                }

                assertEquals(publicCollectionPath, collectionPath)
                assertThat("activity is finishing", activity.isFinishing)
            }
        }
    }

    private val collectionPath: String?
        get() = targetContext.sharedPrefs().getString(CollectionHelper.PREF_COLLECTION_PATH, null)

    @Test
    fun `error toast is shown if EXTRA_PERMISSIONS_SET is missing`() {
        testInvalidActivityFinishes()
        assertThat(
            ShadowToast.getTextOfLatestToast(),
            equalTo(getResourceString(CommonString.something_wrong)),
        )
    }

    @Test
    fun `Each screen starts normally and has the same permissions of a PermissionSet`() {
        testActivity { activity ->
            val permissionSets: List<PermissionSet> = StoragePermissionSet.entries + OptionalPermissionSet.entries
            for (permissionSet in permissionSets) {
                val fragmentClass = permissionSet.permissionsFragment
                if (fragmentClass == null) {
                    assertThat(permissionSet.permissions, empty())
                    continue
                }
                val fragment = fragmentClass.getDeclaredConstructor().newInstance()
                activity.supportFragmentManager.commitNow {
                    replace(R.id.fragment_container, fragment)
                }
                val allPermissions = fragment.permissionsItems.flatMap { it.permissions }

                assertThat(permissionSet.permissions, containsInAnyOrder(allPermissions))
            }
        }
    }

    private fun testInvalidActivityFinishes() {
        val ex = assertFailsWith<NullPointerException> { testActivity(permissionSet = null) { } }
        assertEquals("Cannot run onActivity since Activity has been destroyed already", ex.message)
    }

    private fun testActivity(
        permissionSet: StoragePermissionSet? = ARBITRARY_PERMISSION_SET,
        action: ActivityAction<PermissionsActivity>,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent =
            if (permissionSet != null) {
                PermissionsActivity.getIntent(context, permissionSet)
            } else {
                Intent(context, PermissionsActivity::class.java)
            }
        ActivityScenario.launch<PermissionsActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                action.perform(activity)
            }
        }
    }

    companion object {
        val ARBITRARY_PERMISSION_SET = StoragePermissionSet.entries.first()
    }
}
