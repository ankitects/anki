/*
 *
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.tests.InstrumentedTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NotetypeTest : InstrumentedTest() {
    private val testCol = emptyCol

    @After
    fun tearDown() {
        testCol.close()
    }

    @Test
    fun bigQuery() {
        assumeTrue(
            "This test is flaky on API29, ignoring",
            Build.VERSION.SDK_INT != Build.VERSION_CODES.Q,
        )
        val noteTypes = testCol.notetypes
        val noteType = noteTypes.all()[0]
        val testString = "test"
        val size = testString.length * 1024 * 1024
        val buf = StringBuilder((size * 1.01).toInt())
        // * 1.01 for padding
        for (i in 0 until 1024 * 1024) {
            buf.append(testString)
        }
        noteType.jsonObject.put(testString, buf.toString())
        // Buf should be more than 4MB, so at least two chunks from database.

        // Reload models
        testCol.load()
        val newNoteType = noteTypes.all()[0]
        assertEquals(newNoteType, noteType)
    }
}
