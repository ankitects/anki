// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compat

import com.ichi2.testutils.createTransientDirectory
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers.anExistingDirectory
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.io.File
import java.io.IOException
import kotlin.test.assertFailsWith

class CreateDirectoriesTest : Test21And26() {
    private val rootDirectory = createTransientDirectory()

    @Test
    fun if_directory_does_not_exist_it_is_created() {
        val parent = rootDirectory.createTransientDirectory("1")
        val child = parent.createTransientDirectory("2").also { it.delete() }
        parent.delete()

        assertThat("parent should not exist", parent, not(anExistingDirectory()))
        assertThat("child should not exist", child, not(anExistingDirectory()))

        compat.createDirectories(child)

        assertThat("parent should be created", parent, anExistingDirectory())
        assertThat("child should be created", child, anExistingDirectory())
    }

    @Test
    fun if_directory_exists_nothing_happens() {
        val existing = rootDirectory.createTransientDirectory("2")
        assertDoesNotThrow { compat.createDirectories(existing) }
    }

    @Test
    fun parent_is_a_file() {
        val file =
            File(rootDirectory, "a").apply {
                createNewFile()
                deleteOnExit()
            }
        val child = File(file, "child")
        // We fail as it's a file
        assertFailsWith<IOException> { compat.createDirectories(child) }
    }

    @Test
    fun exception_if_directory_cannot_be_created() {
        val file =
            File(rootDirectory, "a").apply {
                createNewFile()
                deleteOnExit()
            }
        // We fail as it's a file
        assertFailsWith<IOException> { compat.createDirectories(file) }
    }
}
