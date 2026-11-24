// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.NetworkUtils
import io.mockk.every
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.android.controller.ActivityController
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerLoginSyncStateTest : RobolectricTest() {
    @Before
    fun prepareCollection() {
        setIntroductionSlidesShown(true)
        addBasicNote()
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        InitialActivity.setUpgradedToLatestVersion(targetContext.sharedPrefs())
        targetContext.sharedPrefs().edit {
            putBoolean("backupPromptDisabled", true)
            putBoolean(getResourceString(R.string.automatic_sync_choice_key), false)
        }
    }

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `launch login sync is not repeated after recreation`() {
        val controller = launch(autoSync = true)
        assertEquals(1, controller.get().syncRequests)

        assertNoSyncAfterRecreation(controller)
    }

    @Test
    fun `an existing activity still handles a new login request`() {
        val controller = launch(autoSync = false)
        assertEquals(0, controller.get().syncRequests)
        controller.pause().newIntent(loginIntent(autoSync = true)).resume()
        advanceRobolectricLooper()
        assertEquals(1, controller.get().syncRequests)
    }

    @Test
    fun `back exits without syncing when the collection is closed`() {
        val activity = launch(autoSync = false).get()
        CollectionManager.closeCollectionBlocking()
        getPreferences().edit { putBoolean(getResourceString(R.string.automatic_sync_choice_key), true) }
        Prefs.hkey = "test"
        Prefs.allowSyncOnMeteredConnections = true
        Prefs.lastSyncTime = 0

        mockkObject(NetworkUtils)
        try {
            every { NetworkUtils.isOnline } returns true

            activity.onBackPressedDispatcher.onBackPressed()
            advanceRobolectricLooperUntil { activity.isFinishing }

            assertTrue(activity.isFinishing)
            assertFalse(CollectionManager.isOpenUnsafe())
            assertEquals(0L, Prefs.lastSyncTime, "Skipping sync must not update the last sync time")
            assertEquals(0, activity.syncRequests)
        } finally {
            unmockkObject(NetworkUtils)
        }
    }

    private fun launch(autoSync: Boolean): ActivityController<SyncCountingDeckPicker> =
        startActivityControllerNormallyOpenCollectionWithIntent(SyncCountingDeckPicker::class.java, loginIntent(autoSync))

    private fun loginIntent(autoSync: Boolean): Intent =
        DeckPicker.getIntent(targetContext, autoSync).setClass(targetContext, SyncCountingDeckPicker::class.java)

    private fun assertNoSyncAfterRecreation(controller: ActivityController<SyncCountingDeckPicker>) {
        val original = controller.get()
        controller.recreate()
        advanceRobolectricLooper()

        assertNotSame(original, controller.get())
        assertEquals(0, controller.get().syncRequests, "Recreation must not request another sync")
    }

    class SyncCountingDeckPicker : DeckPicker() {
        var syncRequests = 0
            private set

        override fun sync(conflict: ConflictResolution?) {
            syncRequests++
        }
    }
}
