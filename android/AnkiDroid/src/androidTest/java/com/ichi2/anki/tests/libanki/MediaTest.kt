/*
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.tests.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.BackupManager
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Media
import com.ichi2.anki.libanki.exception.EmptyMediaException
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.testutil.GrantStoragePermission
import com.ichi2.anki.testutil.addNote
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
@RunWith(AndroidJUnit4::class)
class MediaTest : InstrumentedTest() {
    private val testCol: Collection = emptyCol

    @get:Rule
    var runtimePermissionRule = GrantStoragePermission.instance

    @After
    fun tearDown() {
        testCol.close()
    }

    @Test
    @Throws(IOException::class, EmptyMediaException::class)
    fun testAdd() {
        // open new empty collection
        val dir = testDir
        BackupManager.removeDir(dir)
        assertTrue(dir.mkdirs())
        val path = File(dir, "foo.jpg")
        FileOutputStream(path, false).use { os ->
            os.write("hello".toByteArray())
        }
        // new file, should preserve name
        val r = testCol.media.addFile(path)
        assertEquals("foo.jpg", r)
        // adding the same file again should not create a duplicate
        assertEquals("foo.jpg", testCol.media.addFile(path))
        // but if it has a different md5, it should
        FileOutputStream(path, false).use { os ->
            os.write("world".toByteArray())
        }
        assertNotEquals("foo.jpg", testCol.media.addFile(path))
    }

    @Test
    @Throws(IOException::class)
    fun testAddEmptyFails() {
        // open new empty collection
        val dir = testDir
        BackupManager.removeDir(dir)
        assertTrue(dir.mkdirs())
        val path = File(dir, "foo.jpg")
        assertTrue(path.createNewFile())

        // new file, should preserve name
        try {
            testCol.media.addFile(path)
            fail("exception should be thrown")
        } catch (_: EmptyMediaException) {
            // all good
        }
    }

    @Test
    @Throws(IOException::class, EmptyMediaException::class)
    fun testDeckIntegration() {
        // create a media dir
        val mediaDir = testCol.media.dir
        // Put a file into it
        val file = createNonEmptyFile("fake.png")
        testCol.media.addFile(file)
        // add a note which references it
        var f = testCol.newNote(testCol.notetypes.current())
        f.setField(0, "one")
        f.setField(1, "<img src='fake.png'>")
        testCol.addNote(f)
        // and one which references a non-existent file
        f = testCol.newNote(testCol.notetypes.current())
        f.setField(0, "one")
        f.setField(1, "<img src='fake2.png'>")
        testCol.addNote(f)
        // and add another file which isn't used
        FileOutputStream(File(mediaDir, "foo.jpg"), false).use { os ->
            os.write("test".toByteArray())
        }
        // check media
        val ret = testCol.media.check()
        assertContains(ret.missingList, "fake2.png")
        assertContains(ret.unusedList, "foo.jpg")
    }

    private fun createNonEmptyFile(
        @Suppress("SameParameterValue") fileName: String,
    ) = File(testDir, fileName).apply {
        writeText("a")
    }
}
