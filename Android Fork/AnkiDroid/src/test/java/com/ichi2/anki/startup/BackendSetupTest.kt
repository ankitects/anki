// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.content.Context
import android.system.Os
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assume.assumeFalse
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class BackendSetupTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `backend preserves a usable inherited temporary directory and removes its probe`() {
        val directory = temporaryFolder.newFolder()
        val existingFile = File(directory, "existing.txt").apply { writeText("keep") }
        assertBackendConfiguresTmpDir(directory.path, overwrite = false)

        assertEquals(listOf(existingFile), directory.listFiles()!!.toList())
        assertEquals("keep", existingFile.readText())
    }

    @Test
    fun `backend uses app cache when TMPDIR is unset`() {
        assertBackendConfiguresTmpDir(null, overwrite = true)
    }

    @Test
    fun `backend uses app cache when TMPDIR is empty`() {
        assertBackendConfiguresTmpDir("", overwrite = true)
    }

    @Test
    fun `backend replaces a missing temporary directory with app cache`() {
        val missingDirectory = File(temporaryFolder.root, "missing")
        assertBackendConfiguresTmpDir(missingDirectory.path, overwrite = true)
        assertFalse(missingDirectory.exists())
    }

    @Test
    fun `backend replaces TMPDIR pointing to a file with app cache`() {
        val file = temporaryFolder.newFile().apply { writeText("keep") }
        assertBackendConfiguresTmpDir(file.path, overwrite = true)
        assertEquals("keep", file.readText())
    }

    @Test
    fun `backend replaces an unwritable temporary directory with app cache`() {
        val directory = temporaryFolder.newFolder()
        assumeTrue("The filesystem must support making directories unwritable", directory.setWritable(false, false))
        try {
            // A privileged test runner may still be able to write despite these permissions.
            assumeFalse(directory.canWrite())
            assertBackendConfiguresTmpDir(directory.path, overwrite = true)
            assertTrue(directory.listFiles()!!.isEmpty())
        } finally {
            assertTrue(directory.setWritable(true, true))
        }
    }

    private fun assertBackendConfiguresTmpDir(
        tmpDir: String?,
        overwrite: Boolean,
    ) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Robolectric does not implement setenv/getenv; the filesystem probe uses real files.
        mockkStatic(Os::class)
        every { Os.getenv("TMPDIR") } returns tmpDir
        justRun { Os.setenv(any(), any(), any()) }
        configureBackendTemporaryDirectory(context)

        verify(exactly = 1) {
            Os.setenv("TMPDIR", context.cacheDir.path, overwrite)
        }
    }
}
