// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.seconds

class MultimediaArgsStorageTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val now = 1_800_000_000_000L

    @Test
    fun `cleanup removes only arguments older than seven days`() {
        val expired = createFile("multimedia-args-expired.tmp", 7.days + 1.seconds)
        val atCutoff = createFile("multimedia-args-cutoff.tmp", 7.days)
        val recent = createFile("multimedia-args-recent.tmp", 1.days)
        val future = createFile("multimedia-args-future.tmp", (-1).days)

        createStorage().removeExpiredFiles(now)

        assertFalse(expired.exists())
        assertTrue(atCutoff.exists())
        assertTrue(recent.exists())
        assertTrue(future.exists())
    }

    @Test
    fun `cleanup preserves unrelated files and directories even when expired`() {
        val unrelated = createFile("ids123.tmp", 8.days)
        val differentSuffix = createFile("multimedia-args123.json", 8.days)
        val directory = temporaryFolder.newFolder("multimedia-args-directory.tmp")
        assertTrue(directory.setLastModified(now - 8.days.inWholeMilliseconds))

        createStorage().removeExpiredFiles(now)

        assertTrue(unrelated.exists())
        assertTrue(differentSuffix.exists())
        assertTrue(directory.isDirectory)
    }

    private fun createStorage(): MultimediaArgsStorage {
        val context = mockk<Context>()
        every { context.cacheDir } returns temporaryFolder.root
        return MultimediaArgsStorage.create(context)
    }

    private fun createFile(
        name: String,
        age: Duration,
    ): File =
        temporaryFolder.newFile(name).apply {
            assertTrue(setLastModified(now - age.inWholeMilliseconds))
        }
}
