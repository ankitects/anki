// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.startup

import android.system.Os
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.testutils.common.assertThrows
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.exceptions.BackendIoException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.DataInputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile

@RunWith(AndroidJUnit4::class)
class BackendSetupIntegrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext

    @Test
    fun collectionExportRecoversFromUnwritableTmpDir() {
        // TemporaryFolder uses reflection that is incompatible with Android's desugared NIO APIs.
        val directory = Files.createTempDirectory(context.cacheDir.toPath(), "backend-setup-").toFile()
        try {
            withTmpDir("/data/local/tmp") {
                // Issue 19545: the export destination is writable, but Rust creates its
                // dummy collection in TMPDIR, which the app cannot write to.
                val failure = assertThrows<BackendIoException> { exportCollection(directory) }
                assertThat(failure.message, containsString("Failed to create file in '/data/local/tmp'"))
                assertThat(failure.message, containsString("Permission denied (os error 13)"))

                AnkiDroidApp.makeBackendUsable(context)

                exportCollection(directory).assertValidCollectionPackage()
                assertThat(Os.getenv("TMPDIR"), equalTo(context.cacheDir.path))
            }
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun exportCollection(directory: File): File =
        Backend().use { backend ->
            val collection = File(directory, "collection.anki2")
            val output = File(directory, "collection.colpkg")
            backend.openCollection(collection.path)
            backend.exportCollectionPackage(output.path, includeMedia = false, legacy = false)
            output
        }

    private fun File.assertValidCollectionPackage() {
        ZipFile(this).use { archive ->
            assertThat(archive.getEntry("collection.anki21b"), notNullValue())
            // This dummy SQLite collection is the temporary file that previously failed.
            val dummyCollection = archive.getEntry("collection.anki2")
            assertThat(dummyCollection, notNullValue())
            DataInputStream(archive.getInputStream(dummyCollection)).use { input ->
                val header = ByteArray(16)
                input.readFully(header)
                assertThat(header.toString(Charsets.US_ASCII), equalTo("SQLite format 3\u0000"))
            }
        }
    }

    private fun withTmpDir(
        @Suppress("SameParameterValue") path: String,
        block: () -> Unit,
    ) {
        val originalTmpDir = Os.getenv("TMPDIR")
        try {
            Os.setenv("TMPDIR", path, true)
            block()
        } finally {
            if (originalTmpDir == null) {
                Os.unsetenv("TMPDIR")
            } else {
                Os.setenv("TMPDIR", originalTmpDir, true)
            }
        }
    }
}
