// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.utils.ext

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Tests for [File.containsFile] */
class ContainsFileTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `finds an existing file`() {
        temp.newFile("collection.anki2")
        assertTrue(temp.root.containsFile("collection.anki2"))
    }

    @Test
    fun `false when the file does not exist`() {
        assertFalse(temp.root.containsFile("collection.anki2"))
    }

    @Test
    fun `false when the name points to a directory`() {
        temp.newFolder("collection.anki2")
        assertFalse(temp.root.containsFile("collection.anki2"))
    }

    @Test
    fun `finds a file in a subdirectory`() {
        temp.newFolder("sub")
        temp.newFile("sub/collection.anki2")
        assertTrue(temp.root.containsFile("sub/collection.anki2"))
    }

    @Test
    fun `false for a path escaping the directory`() {
        temp.newFile("secret.txt")
        val sub = temp.newFolder("sub")
        assertFalse(sub.containsFile("../secret.txt"))
    }

    @Test
    fun `false for an empty name`() {
        assertFalse(temp.root.containsFile(""))
    }

    @Test
    fun `false when the receiver is not a directory`() {
        val file = temp.newFile("plain.txt")
        assertFalse(file.containsFile("anything"))
    }
}
