// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Arthur Milchior <Arthur@Milchior.fr>

package com.ichi2.compat

import android.annotation.SuppressLint
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.createTransientFile
import com.ichi2.testutils.withTempFile
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.instanceOf
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import java.io.FileNotFoundException
import java.io.IOException
import java.nio.file.NotDirectoryException
import kotlin.test.assertFailsWith

class CompatDirectoryContentTest : Test21And26() {
    @Test
    fun empty_dir_test() {
        val directory = createTransientDirectory()
        compat.contentOfDirectory(directory).use {
            assertThat("Iterator should not have next", it.hasNext(), equalTo(false))
        }
    }

    @Test
    fun ensure_absolute_path() {
        // Relative paths caused me hours of debugging. Never again.
        val directory =
            createTransientDirectory()
                .withTempFile("zero")
        val iterator = compat.contentOfDirectory(directory)
        val file = iterator.next()
        assertThat("Paths should be absolute", file.path, equalTo(file.absolutePath))
    }

    @Test
    fun dir_test_three_files() {
        val directory =
            createTransientDirectory()
                .withTempFile("zero")
                .withTempFile("one")
                .withTempFile("two")
        compat.contentOfDirectory(directory).use { iterator ->
            val found = Array(3) { false }
            for (i in 1..3) {
                assertThat("Iterator should have a $i-th element", iterator.hasNext(), equalTo(true))
                val file = iterator.next()
                val fileNumber =
                    when (file.name) {
                        "zero" -> 0
                        "one" -> 1
                        "two" -> 2
                        else -> -1
                    }
                assertThat("File ${file.name} should not be in ${directory.path}", fileNumber, not(equalTo(-1)))
                assertThat("File ${file.name} should not be listed twice", found[fileNumber], equalTo(false))
                found[fileNumber] = true
            }
            assertThat("Iterator should not have next anymore", iterator.hasNext(), equalTo(false))
        }
    }

    @Test
    fun non_existent_dir_test() {
        val directory = createTransientDirectory()
        directory.delete()
        assertFailsWith<FileNotFoundException> { compat.contentOfDirectory(directory) }
    }

    @SuppressLint("NewApi")
    @Test
    fun file_test() {
        val file = createTransientFile("foo")
        val exception = assertFailsWith<IOException> { compat.contentOfDirectory(file) }
        if (isV26) {
            assertThat(
                "Starting at API 26, this should be a NotDirectoryException",
                exception,
                instanceOf(NotDirectoryException::class.java),
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
        assertFailsWith<IOException> { permissionDenied.compat.contentOfDirectory(permissionDenied.directory.directory) }
    }
}
