// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compat

import android.os.Build
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.createTransientFile
import com.ichi2.testutils.withTempFile
import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NotDirectoryException
import kotlin.test.assertFailsWith

/** Tests for [hasFiles] */
class HasFilesTest : Test21And26() {
    @Test
    fun has_files_with_file() {
        val dir = createTransientDirectory().withTempFile("aa.txt")
        assertThat("empty directory has no files", hasFiles(dir), equalTo(true))
    }

    @Test
    fun has_files_exists() {
        val dir = createTransientDirectory()
        assertThat("empty directory has no files", hasFiles(dir), equalTo(false))
    }

    @Test
    fun has_files_not_exists() {
        val dir = createTransientDirectory()
        dir.delete()

        assertFailsWith<FileNotFoundException> { hasFiles(dir) }
    }

    @Test
    fun has_files_on_file() {
        val file = createTransientFile("hello")

        val exception = assertFailsWith<IOException> { hasFiles(file) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertThat(
                "Starting at API 26, this should be a NotDirectoryException",
                exception,
                CoreMatchers.instanceOf(NotDirectoryException::class.java),
            )
        }
    }

    /**
     * Reproduces https://github.com/ankidroid/Anki-Android/issues/10358
     * Where for some reason, `listFiles` returned null on an existing directory and
     * newDirectoryStream returned `AccessDeniedException`.
     */
    @Test
    fun reproduce_10358() {
        val permissionDenied = createPermissionDenied()
        assertFailsWith<IOException> { permissionDenied.compat.hasFiles(permissionDenied.directory.directory) }
    }

    private fun hasFiles(dir: File) = compat.hasFiles(dir)
}
