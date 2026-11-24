// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

class IdSnapshotFileTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = 1_800_000_000_000L
    private lateinit var cacheDirectory: File
    private val snapshotDirectory get() = IdSnapshotFile.getDirectory(cacheDirectory)

    @Before
    fun setUpStorage() {
        TimeManager.resetWith(MockTime(now))
        cacheDirectory = temporaryFolder.newFolder("cache")
    }

    @After
    fun resetTime() = TimeManager.reset()

    @Test
    fun `snapshot directory is created only when writing`() {
        val directory = snapshotDirectory
        assertEquals(File(cacheDirectory, "idsfiles"), directory)
        assertFalse(directory.exists())
        IdSnapshotFile.create(directory, listOf(42L), "ids")
        assertTrue(directory.isDirectory)
    }

    @Test
    fun `empty selection is distinct from a missing file`() {
        val file = IdSnapshotFile.create(temporaryFolder.root, emptyList(), "ids")
        assertEquals(emptyList(), file.getIdsOrNull())
        assertTrue(file.delete())
        assertNull(file.getIdsOrNull())
    }

    @Test
    fun `incomplete header cannot be read`() {
        val file = IdSnapshotFile.create(temporaryFolder.root, emptyList(), "ids")
        file.writeBytes(byteArrayOf(0, 0))
        assertFailsWith<IOException> { file.getIdsOrNull() }
    }

    @Test
    fun `incomplete selections are rejected before allocating the declared count`() {
        val file = IdSnapshotFile.create(temporaryFolder.root, emptyList(), "ids")
        for (count in listOf(2, Int.MAX_VALUE)) {
            DataOutputStream(file.outputStream()).use {
                it.writeInt(count)
                it.writeLong(1L)
            }
            assertFailsWith<IOException>("count: $count") { file.getIdsOrNull() }
        }
    }

    @Test
    fun `negative counts and trailing data cannot be read`() {
        val file = IdSnapshotFile.create(temporaryFolder.root, emptyList(), "ids")
        for (count in listOf(-1, 0)) {
            DataOutputStream(file.outputStream()).use {
                it.writeInt(count)
                it.writeLong(1L)
            }
            assertFailsWith<IOException>("count: $count") { file.getIdsOrNull() }
        }
    }

    @Test
    fun `directory creation fails clearly when a file occupies the path`() {
        val path = File(cacheDirectory, "idsfiles").apply { writeText("occupied") }
        val exception =
            assertFailsWith<IOException> {
                IdSnapshotFile.create(snapshotDirectory, listOf(1L), "ids")
            }
        assertTrue(exception.message.orEmpty().contains(path.path))
    }

    @Test
    fun `cleanup removes only files older than the retention boundary`() {
        val directory = snapshotDirectory
        val expired = IdSnapshotFile.create(directory, listOf(1L), "ids").aged(7.days.inWholeMilliseconds + 1)
        val boundary = IdSnapshotFile.create(directory, listOf(2L), "ids").aged(7.days.inWholeMilliseconds)
        val recent = IdSnapshotFile.create(directory, listOf(3L), "ids").aged(1.days.inWholeMilliseconds)
        val future = IdSnapshotFile.create(directory, listOf(4L), "ids").aged(-1.days.inWholeMilliseconds)
        val unknownAge = IdSnapshotFile.create(directory, listOf(5L), "ids").apply { assertTrue(setLastModified(0)) }
        val subdirectory = File(directory, "ids123.tmp").apply { mkdir() }.aged(8.days.inWholeMilliseconds)

        IdSnapshotFile.deleteStaleFiles(snapshotDirectory)

        assertFalse(expired.exists())
        listOf(boundary, recent, future, unknownAge, subdirectory).forEach { assertTrue(it.exists(), it.name) }
    }

    @Test
    fun `cleanup continues after a deletion throws`() {
        var deletionAttempts = 0
        val failedFile =
            object : File("ids123.tmp") {
                override fun isFile() = true

                override fun lastModified() = now - 8.days.inWholeMilliseconds

                override fun delete(): Boolean {
                    deletionAttempts++
                    throw SecurityException("denied")
                }
            }
        val expired = IdSnapshotFile.create(snapshotDirectory, listOf(1L), "ids").aged(8.days.inWholeMilliseconds)
        val directory =
            object : File(expired.parent) {
                override fun listFiles(): Array<File> = arrayOf(failedFile, expired)
            }
        IdSnapshotFile.deleteStaleFiles(directory)
        assertEquals(1, deletionAttempts)
        assertFalse(expired.exists())
    }

    @Test
    fun `cleanup continues when deleting a file returns false`() {
        var deletionAttempts = 0
        val failedFile =
            object : File("ids123.tmp") {
                override fun isFile() = true

                override fun lastModified() = now - 8.days.inWholeMilliseconds

                override fun delete(): Boolean {
                    deletionAttempts++
                    return false
                }
            }
        val expired = IdSnapshotFile.create(snapshotDirectory, listOf(1L), "ids").aged(8.days.inWholeMilliseconds)
        val directory =
            object : File(expired.parent) {
                override fun listFiles(): Array<File> = arrayOf(failedFile, expired)
            }
        IdSnapshotFile.deleteStaleFiles(directory)
        assertEquals(1, deletionAttempts)
        assertFalse(expired.exists())
    }

    @Test
    fun `cleanup does not create unused snapshot directories`() {
        IdSnapshotFile.deleteStaleFiles(snapshotDirectory)
        assertFalse(File(cacheDirectory, "idsfiles").exists())
    }

    @Test
    fun `cleanup tolerates an unreadable snapshot directory`() {
        val directory =
            object : File(snapshotDirectory.path) {
                override fun listFiles(): Array<File> = throw SecurityException("denied")
            }
        val file = IdSnapshotFile.create(directory, listOf(1L), "ids").aged(8.days.inWholeMilliseconds)
        IdSnapshotFile.deleteStaleFiles(directory)
        assertTrue(file.exists())
    }

    private fun <T : File> T.aged(ageMillis: Long): T =
        apply {
            assertTrue(setLastModified(now - ageMillis))
        }
}
