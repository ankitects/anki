// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.exception.CollectionLockedException
import com.ichi2.testutils.OnDiskJvmTest
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.BackendException.BackendImportException
import net.ankiweb.rsdroid.exceptions.BackendIoException
import net.ankiweb.rsdroid.exceptions.BackendSyncException
import org.junit.Test
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull

class CollectionManagerImportTest : OnDiskJvmTest() {
    @Test
    fun `invalid backup leaves the original collection open`() =
        runTest {
            val backup = collectionDirectory.newFile("invalid.colpkg").apply { writeText("invalid backup") }

            assertFailedImportKeepsCollectionOpen<BackendSyncException>(backup)
        }

    @Test
    fun `missing backup leaves the original collection open`() =
        runTest {
            assertFailedImportKeepsCollectionOpen<BackendIoException>(File(collectionDirectory.root, "missing.colpkg"))
        }

    @Test
    fun `corrupt database in a valid archive leaves the original collection open`() =
        runTest {
            val failure = assertFailedImportKeepsCollectionOpen<BackendImportException>(corruptDatabaseBackup())

            assertEquals(CollectionManager.TR.importingTheProvidedFileIsNotA(), failure.localizedMessage)
        }

    @Test
    fun `failure to reopen does not replace the import error`() =
        runTest {
            addBasicNote()
            val backup = corruptDatabaseBackup()
            val expectedMessage = CollectionManager.TR.importingTheProvidedFileIsNotA()
            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            try {
                val failure = assertFailsWith<BackendImportException> { CollectionManager.importColpkg(backup.path) }

                assertEquals(expectedMessage, failure.localizedMessage)
                assertIs<CollectionLockedException>(failure.suppressed.single())
            } finally {
                CollectionManager.emulatedOpenFailure = null
            }
        }

    private fun corruptDatabaseBackup(): File =
        collectionDirectory.newFile("corrupt.colpkg").apply {
            ZipOutputStream(outputStream()).use { archive ->
                archive.putNextEntry(ZipEntry("collection.anki2"))
                archive.write("not a SQLite database".toByteArray())
                archive.closeEntry()
            }
        }

    private suspend inline fun <reified T : BackendException> assertFailedImportKeepsCollectionOpen(backup: File): T {
        val note = addBasicNote("original", "answer")

        val failure = assertFailsWith<T> { CollectionManager.importColpkg(backup.path) }

        // withCol would reopen the collection and hide the regression.
        val fields = CollectionManager.withOpenColOrNull { getNote(note.id).fields.toList() }
        assertNotNull(fields, "The original collection should already be open after the import fails")
        assertEquals(listOf("original", "answer"), fields)
        return failure
    }
}
