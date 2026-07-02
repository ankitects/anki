// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import com.ichi2.anki.compat.hasFiles
import com.ichi2.compat.Test21And26
import com.ichi2.testutils.HamcrestUtils.containsInAnyOrder
import com.ichi2.testutils.withTempFile
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import kotlin.io.path.createTempDirectory
import kotlin.io.path.pathString
import kotlin.test.assertFailsWith

/**
 * Tests for [Directory]
 */
class DirectoryTest : Test21And26() {
    @Test
    fun passes_if_existing_directory() {
        val path = createTempDirectory().pathString
        MatcherAssert.assertThat(
            "Directory should work with valid directory",
            Directory.createInstance(path),
            not(nullValue()),
        )
    }

    @Test
    fun fails_if_does_not_exist() {
        val subdirectory = File(createTempDirectory().pathString, "aa")
        MatcherAssert.assertThat(
            "Directory requires an existing directory",
            Directory.createInstance(subdirectory),
            nullValue(),
        )
    }

    @Test
    fun fails_if_file() {
        val dir =
            kotlin.io.path
                .createTempFile()
                .pathString
        MatcherAssert.assertThat(
            "file should not become a Directory",
            Directory.createInstance(dir),
            nullValue(),
        )
    }

    @Test
    fun list_files_returns_valid_paths() {
        val dir =
            createValidTempDir()
                .withTempFile("foo.txt")
                .withTempFile("bar.xtx")
                .withTempFile("baz.xtx")

        val files = dir.listFiles()

        MatcherAssert.assertThat(
            "Directory should contain only three files",
            files.toList(),
            containsInAnyOrder(listOf("foo.txt", "bar.xtx", "baz.xtx").map { File(dir.directory, it) }),
        )
    }

    @Test
    fun has_files_is_false_if_empty() {
        val dir = createValidTempDir()
        MatcherAssert.assertThat(
            "empty directory should not have files",
            dir.hasFiles(),
            equalTo(false),
        )
    }

    @Test
    fun has_files_throws_if_file_no_longer_exists() {
        val dir = createValidTempDir()
        dir.directory.delete()
        assertFailsWith<FileNotFoundException> { dir.hasFiles() }
    }

    @Test
    fun has_files_is_true_if_file() {
        val dir = createValidTempDir()
        File(dir.directory, "aa.txt").writeText("aa")
        MatcherAssert.assertThat(
            "non-empty directory should have files",
            dir.hasFiles(),
            equalTo(true),
        )
    }

    @Test
    fun test_create_from_string() {
        val path = File(createTempDirectory().pathString)

        val fromPath = Directory.createInstance(path.path)!!
        val fromFile = Directory.createInstance(path)!!

        MatcherAssert.assertThat("Equal result constructing from file or path", fromFile.directory, equalTo(fromPath.directory))
    }

    /**
     * Reproduces https://github.com/ankidroid/Anki-Android/issues/10358
     * Where for some reason, `listFiles` returned null on an existing directory and
     * newDirectoryStream returned `AccessDeniedException`.
     */
    @Test
    fun reproduce_10358() {
        val permissionDenied = createPermissionDenied()
        permissionDenied.assertThrowsWhenPermissionDenied { permissionDenied.directory.hasFiles() }
    }

    private fun createValidTempDir(): Directory = Directory.createInstance(createTempDirectory().pathString)!!
}
