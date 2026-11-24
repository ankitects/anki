// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.deckpicker.DeckPickerProgress
import com.ichi2.anki.progress.ViewModelProgress
import com.ichi2.testutils.BackupManagerTestUtilities
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class DeckPickerBackupTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `create backup reports when collection is unchanged`() =
        withBackupDeckPicker { deckPicker ->
            assertTrue(
                col.createBackup(
                    BackupManager.getBackupDirectoryFromCollection(col.colDb),
                    force = true,
                    waitForCompletion = true,
                ),
            )

            deckPicker.createBackupAndWait()

            assertEquals(TR.profilesBackupUnchanged(), ShadowToast.getTextOfLatestToast())
            assertEquals(1, BackupManager.getBackups(col.colDb).size)
        }

    @Test
    fun `create backup reports success after writing backup`() =
        withBackupDeckPicker { deckPicker ->
            deckPicker.createBackupAndWait()

            assertEquals(TR.profilesBackupCreated(), ShadowToast.getTextOfLatestToast())
            assertEquals(1, BackupManager.getBackups(col.colDb).size)
        }

    @Test
    fun `create backup reports progress`() =
        withBackupDeckPicker { deckPicker ->
            deckPicker.viewModel.progressManager.progress.test {
                assertIs<ViewModelProgress.Idle>(awaitItem())
                deckPicker.createBackupAndWait()
                val active = assertIs<ViewModelProgress.Active<DeckPickerProgress>>(awaitItem())
                assertEquals(DeckPickerProgress.CREATING_BACKUP, active.message)
                assertIs<ViewModelProgress.Idle>(awaitItem())
            }
        }

    private fun withBackupDeckPicker(block: suspend (DeckPicker) -> Unit) =
        runTest {
            lateinit var deckPicker: DeckPicker
            withDeckPicker(deckCount = 0, withCards = true) { deckPicker = it }
            // Dispatch the IO continuation back to the test thread before updating the UI.
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            block(deckPicker)
        }

    private suspend fun DeckPicker.createBackupAndWait() {
        ShadowToast.reset()
        createBackup().join()
    }
}
