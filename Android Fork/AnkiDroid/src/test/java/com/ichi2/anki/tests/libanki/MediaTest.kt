// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>

package com.ichi2.anki.tests.libanki

import com.ichi2.anki.libanki.Media
import com.ichi2.anki.libanki.exception.EmptyMediaException
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.testutils.OnDiskJvmTest
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Unit tests for [Media].
 */
class MediaTest : OnDiskJvmTest() {
    @Test
    @Throws(IOException::class, EmptyMediaException::class)
    fun testAdd() {
        val dir = collectionDirectory.newFolder()
        val path = File(dir, "foo.jpg")
        FileOutputStream(path, false).use { os ->
            os.write("hello".toByteArray())
        }
        // new file, should preserve name
        val r = col.media.addFile(path)
        assertEquals("foo.jpg", r)
        // adding the same file again should not create a duplicate
        assertEquals("foo.jpg", col.media.addFile(path))
        // but if it has a different md5, it should
        FileOutputStream(path, false).use { os ->
            os.write("world".toByteArray())
        }
        assertNotEquals("foo.jpg", col.media.addFile(path))
    }

    @Test
    @Throws(IOException::class)
    fun testAddEmptyFails() {
        val dir = collectionDirectory.newFolder()
        val path = File(dir, "foo.jpg")
        assertTrue(path.createNewFile())

        // new file, should preserve name
        try {
            col.media.addFile(path)
            fail("exception should be thrown")
        } catch (_: EmptyMediaException) {
            // all good
        }
    }

    @Test
    @Throws(IOException::class, EmptyMediaException::class)
    fun testDeckIntegration() {
        // create a media dir
        val mediaDir = col.media.dir
        // Put a file into it
        val file = createNonEmptyFile("fake.png")
        col.media.addFile(file)
        // add a note which references it
        var f = col.newNote(col.notetypes.current())
        f.setField(0, "one")
        f.setField(1, "<img src='fake.png'>")
        col.addNote(f)
        // and one which references a non-existent file
        f = col.newNote(col.notetypes.current())
        f.setField(0, "one")
        f.setField(1, "<img src='fake2.png'>")
        col.addNote(f)
        // and add another file which isn't used
        FileOutputStream(File(mediaDir, "foo.jpg"), false).use { os ->
            os.write("test".toByteArray())
        }
        // check media
        val ret = col.media.check()
        assertContains(ret.missingList, "fake2.png")
        assertContains(ret.unusedList, "foo.jpg")
    }

    private fun createNonEmptyFile(
        @Suppress("SameParameterValue") fileName: String,
    ) = File(collectionDirectory.root, fileName).apply {
        writeText("a")
    }
}
