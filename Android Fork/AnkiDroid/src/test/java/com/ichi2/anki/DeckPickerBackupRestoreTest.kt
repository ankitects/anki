// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.utils.positiveButton
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog
import java.io.File
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

@RunWith(AndroidJUnit4::class)
class DeckPickerBackupRestoreTest : RobolectricTest() {
    override fun getCollectionStorageMode() = CollectionStorageMode.ON_DISK

    @Before
    fun setCollectionPath() {
        getPreferences().edit {
            putString(CollectionHelper.PREF_COLLECTION_PATH, tempFolder.newFolder("collection").absolutePath)
        }
    }

    @After
    fun resetBackupSpace() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun `failed restore reports the error and leaves the original collection open`() {
        val note = addBasicNote("original", "answer")
        val backup = createInvalidBackup()

        withDeckPicker(deckCount = 0) { deckPicker ->
            throwOnShowError = false
            deckPicker.importColpkgListener = ImportColpkgListener { fail("Failed restore reported success") }

            deckPicker.restoreBackupAndWaitForError(backup)

            assertEquals(TR.importingTheProvidedFileIsNotA(), getAlertDialogText(checkDismissed = false))
            // Check before accessing col: that getter would reopen it and hide the regression.
            assertTrue(CollectionManager.isOpenUnsafe(), "Failed restore left the collection closed")

            clickAlertDialogButton { positiveButton }
            assertTrue(CollectionManager.isOpenUnsafe(), "Dismissing the restore error left the collection closed")
            assertEquals(listOf("original", "answer"), col.getNote(note.id).fields.toList())
        }
    }

    @Test
    fun `valid backup replaces the collection`() =
        runTest {
            val note = addBasicNote("backed up", "answer")
            val backupDirectory = tempFolder.newFolder("backups")
            assertTrue(col.createBackup(backupDirectory.path, force = true, waitForCompletion = true))
            val files = assertNotNull(backupDirectory.listFiles(), "Unable to list backup directory: $backupDirectory")
            val backups = files.filter { it.extension == "colpkg" }
            assertEquals(1, backups.size, "Expected one collection backup in $backupDirectory; found ${files.map { it.name }}")
            val backup = backups.single()
            addBasicNote("added after backup", "answer")

            CollectionManager.importColpkg(backup.path)

            CollectionManager.withCol {
                assertEquals(1, noteCount())
                assertEquals("backed up", getNote(note.id).fields[0])
            }
        }

    private fun createInvalidBackup(): File {
        val backupDirectory = BackupManager.getBackupDirectory(CollectionManager.getCollectionDirectory())
        val backup = File(backupDirectory, "backup-2020-07-07-07.00.00.colpkg")
        // A valid ZIP missing its collection produces the localized invalid-backup error.
        ZipOutputStream(backup.outputStream()).use { }
        return backup
    }

    private fun DeckPicker.restoreBackupAndWaitForError(backup: File) {
        assertTrue(shadowOf(this).clickMenuItem(R.id.action_restore_backup))
        advanceRobolectricLooper()
        clickAlertDialogButton { positiveButton }
        val chooser = assertIs<AlertDialog>(ShadowDialog.getLatestDialog())
        val listView = chooser.listView
        assertEquals(1, listView.count)
        assertTrue(
            listView.performItemClick(listView.getChildAt(0), 0, listView.adapter.getItemId(0)),
            "Selecting the backup did not invoke its restore listener",
        )

        advanceRobolectricLooperUntil(lazyMessage = { "Expected a restore error dialog after selecting ${backup.name}" }) {
            val dialog = ShadowDialog.getLatestDialog()
            dialog is AlertDialog && dialog !== chooser && dialog.isShowing
        }
    }
}
