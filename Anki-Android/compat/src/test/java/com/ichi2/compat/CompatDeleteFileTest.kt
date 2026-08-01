// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compat

import android.annotation.SuppressLint
import com.ichi2.anki.compat.BaseCompat
import com.ichi2.anki.compat.Compat
import com.ichi2.anki.compat.CompatV26
import com.ichi2.testutils.createTransientDirectory
import com.ichi2.testutils.createTransientFile
import com.ichi2.testutils.withTempFile
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import kotlin.test.assertFailsWith

/** Tests for [Compat.deleteFile] */
@RunWith(Parameterized::class)
class CompatDeleteFileTest(
    val compat: Compat,
    /** Used in the "Test Results" Window */
    @Suppress("unused") private val unitTestDescription: String,
) {
    companion object {
        @SuppressLint("NewApi")
        @JvmStatic // required for Parameters
        @Parameterized.Parameters(name = "{1}")
        fun data(): Iterable<Array<Any>> =
            sequence {
                yield(arrayOf(BaseCompat(), "BaseCompat"))
                yield(arrayOf(CompatV26(), "CompatV26"))
            }.asIterable()
    }

    @Test
    fun delete_file_which_exists() {
        val file = createTransientFile()
        assertDoesNotThrow { deleteFile(file) }
        assertThat("file should no longer exist", file.exists(), equalTo(false))
    }

    @Test
    fun delete_directory_which_exists() {
        val dir = createTransientDirectory()
        assertDoesNotThrow { deleteFile(dir) }
        assertThat("directory should no longer exist", dir.exists(), equalTo(false))
    }

    @Test
    fun delete_fails_if_exists_is_false() {
        val dir = createTransientDirectory()
        dir.delete()
        assertFailsWith<FileNotFoundException> { deleteFile(dir) }
    }

    @Test
    fun delete_fails_if_not_empty_directory() {
        // Note: Exception is a DirectoryNotEmptyException in V26
        val dir = createTransientDirectory().withTempFile("foo.txt")
        assertFailsWith<IOException> { deleteFile(dir) }
    }

    private fun deleteFile(file: File) = compat.deleteFile(file)
}
