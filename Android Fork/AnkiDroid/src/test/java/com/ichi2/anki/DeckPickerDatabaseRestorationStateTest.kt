// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.Intent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotSame
import kotlin.test.assertSame

@RunWith(AndroidJUnit4::class)
class DeckPickerDatabaseRestorationStateTest : RobolectricTest() {
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
    fun `recreation restores the destination and binds the listener to the new activity`() {
        val previousOverride = CollectionHelper.ankiDroidDirectoryOverride
        try {
            val controller = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent())
            val original = controller.get()
            val destination = tempFolder.newFolder("restored-collection")
            original.importColpkgListener = DatabaseRestorationListener(original, destination)

            controller.recreate()
            advanceRobolectricLooper()

            val recreated = controller.get()
            assertNotSame(original, recreated)
            val listener = assertIs<DatabaseRestorationListener>(recreated.importColpkgListener)
            assertEquals(destination, listener.newAnkiDroidDirectory)
            assertSame(recreated, listener.activity)
        } finally {
            CollectionHelper.ankiDroidDirectoryOverride = previousOverride
        }
    }
}
