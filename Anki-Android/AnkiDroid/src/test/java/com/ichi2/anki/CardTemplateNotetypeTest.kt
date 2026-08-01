/*
 * Copyright (c) 2020 Mike Hardy <mike@mikehardy.net>
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

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardTemplateNotetype.ChangeType.ADD
import com.ichi2.anki.CardTemplateNotetype.ChangeType.DELETE
import com.ichi2.anki.CardTemplateNotetype.TemplateChange
import com.ichi2.anki.compat.CompatHelper.Companion.getSerializableCompat
import com.ichi2.anki.libanki.NotetypeJson
import org.json.JSONObject
import org.junit.Assert
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber
import java.io.IOException
import java.io.Serializable
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

@RunWith(AndroidJUnit4::class)
class CardTemplateNotetypeTest : RobolectricTest() {
    @Test
    @Throws(Exception::class)
    fun testTempNoteTypeStorage() {
        // Start off with clean state in the cache dir
        CardTemplateNotetype.clearTempNoteTypeFiles()

        // Make sure save / retrieve works
        val tempModelPath =
            CardTemplateNotetype.saveTempNoteType(
                targetContext,
                NotetypeJson(
                    """{"foo": "bar"}""",
                ),
            )
        assertNotNull("Saving temp model unsuccessful", tempModelPath)
        val tempModel = CardTemplateNotetype.getTempNoteType(tempModelPath!!)
        assertNotNull("Temp model not read successfully", tempModel)
        Assert.assertEquals(JSONObject("{\"foo\": \"bar\"}").toString(), tempModel.toString())

        // Make sure clearing works
        Assert.assertEquals(1, CardTemplateNotetype.clearTempNoteTypeFiles().toLong())
        Timber.i("The following logged NoSuchFileException is an expected part of verifying a file delete.")
        try {
            CardTemplateNotetype.getTempNoteType(tempModelPath)
            Assert.fail("Should have caught an exception here because the file is missing")
        } catch (e: IOException) {
            // this is expected
        }
    }

    @Test
    fun testAddDeleteTracking() {
        // Assume you start with a 2 template model (like "Basic (and reversed)")
        // Add a 3rd new template, remove the 2nd, remove the 1st, add a new now-2nd, remove 1st again
        // ...and it should reduce to just removing the original 1st/2nd and adding the final as first
        val tempNotetype = CardTemplateNotetype(NotetypeJson("{ \"foo\": \"bar\" }"))

        tempNotetype.addTemplateChange(ADD, 3)
        val expected1 = arrayOf(TemplateChange(3, ADD))
        // 3 templates and one change now
        assertTemplateChangesEqual(expected1, tempNotetype.templateChanges)
        assertTemplateChangesEqual(expected1, tempNotetype.adjustedTemplateChanges)
        Assert.assertArrayEquals(intArrayOf(3), tempNotetype.getDeleteDbOrds(3))

        tempNotetype.addTemplateChange(DELETE, 2)
        // 2 templates and two changes now
        val expected2 = arrayOf(TemplateChange(3, ADD), TemplateChange(2, DELETE))
        val adjExpected2 = arrayOf(TemplateChange(2, ADD), TemplateChange(2, DELETE))
        assertTemplateChangesEqual(expected2, tempNotetype.templateChanges)
        assertTemplateChangesEqual(adjExpected2, tempNotetype.adjustedTemplateChanges)
        Assert.assertArrayEquals(intArrayOf(2, 4), tempNotetype.getDeleteDbOrds(3))

        tempNotetype.addTemplateChange(DELETE, 1)
        // 1 template and three changes now
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempNotetype.getDeleteDbOrds(3))
        val expected3 = arrayOf(TemplateChange(3, ADD), TemplateChange(2, DELETE), TemplateChange(1, DELETE))
        val adjExpected3 = arrayOf(TemplateChange(1, ADD), TemplateChange(2, DELETE), TemplateChange(1, DELETE))
        assertTemplateChangesEqual(expected3, tempNotetype.templateChanges)
        assertTemplateChangesEqual(adjExpected3, tempNotetype.adjustedTemplateChanges)

        tempNotetype.addTemplateChange(ADD, 2)
        // 2 templates and 4 changes now
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempNotetype.getDeleteDbOrds(3))
        val expected4 = arrayOf(TemplateChange(3, ADD), TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(2, ADD))
        val adjExpected4 = arrayOf(TemplateChange(1, ADD), TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(2, ADD))
        assertTemplateChangesEqual(expected4, tempNotetype.templateChanges)
        assertTemplateChangesEqual(adjExpected4, tempNotetype.adjustedTemplateChanges)

        // Make sure we can resurrect these changes across lifecycle
        val outBundle = tempNotetype.toBundle()
        assertTemplateChangesEqual(expected4, outBundle.getSerializableCompat("mTemplateChanges"))

        // This is the hard part. We will delete a template we added so everything shifts.
        // The template currently at ordinal 1 was added as template 3 at the start before it slid down on the deletes
        // So the first template add should be negated by this delete, and the second template add should slide down to 1
        tempNotetype.addTemplateChange(DELETE, 1)
        // 1 template and 3 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempNotetype.getDeleteDbOrds(3))
        val expected5 = arrayOf(TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(1, ADD))
        val adjExpected5 = arrayOf(TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(1, ADD))
        assertTemplateChangesEqual(expected5, tempNotetype.templateChanges)
        assertTemplateChangesEqual(adjExpected5, tempNotetype.adjustedTemplateChanges)

        tempNotetype.addTemplateChange(ADD, 2)
        // 2 template and 4 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempNotetype.getDeleteDbOrds(3))
        val expected6 = arrayOf(TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(1, ADD), TemplateChange(2, ADD))
        val adjExpected6 = arrayOf(TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(1, ADD), TemplateChange(2, ADD))
        assertTemplateChangesEqual(expected6, tempNotetype.templateChanges)
        assertTemplateChangesEqual(adjExpected6, tempNotetype.adjustedTemplateChanges)

        tempNotetype.addTemplateChange(ADD, 3)
        // 2 template and 4 changes now (the delete just cancelled out one of the adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempNotetype.getDeleteDbOrds(3))
        val expected7 =
            arrayOf(
                TemplateChange(2, DELETE),
                TemplateChange(1, DELETE),
                TemplateChange(1, ADD),
                TemplateChange(2, ADD),
                TemplateChange(3, ADD),
            )
        val adjExpected7 =
            arrayOf(
                TemplateChange(2, DELETE),
                TemplateChange(1, DELETE),
                TemplateChange(1, ADD),
                TemplateChange(2, ADD),
                TemplateChange(3, ADD),
            )
        assertTemplateChangesEqual(expected7, tempNotetype.templateChanges)
        assertTemplateChangesEqual(adjExpected7, tempNotetype.adjustedTemplateChanges)

        tempNotetype.addTemplateChange(DELETE, 3)
        // 1 template and 3 changes now (two deletes cancelled out adds)
        Assert.assertArrayEquals(intArrayOf(2, 1, 5), tempNotetype.getDeleteDbOrds(3))
        val expected8 = arrayOf(TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(1, ADD), TemplateChange(2, ADD))
        val adjExpected8 = arrayOf(TemplateChange(2, DELETE), TemplateChange(1, DELETE), TemplateChange(1, ADD), TemplateChange(2, ADD))
        assertTemplateChangesEqual(expected8, tempNotetype.templateChanges)
        assertTemplateChangesEqual(adjExpected8, tempNotetype.adjustedTemplateChanges)
    }

    @Suppress("UNCHECKED_CAST")
    private fun assertTemplateChangesEqual(
        expected: Array<TemplateChange>,
        actual: Serializable?,
    ) {
        if (actual !is ArrayList<*>) {
            Assert.fail("actual array null or not the correct type")
        }
        Assert.assertEquals(
            "arrays didn't have the same length?",
            expected.size.toLong(),
            (actual as ArrayList<TemplateChange>).size.toLong(),
        )
        for (i in expected.indices) {
            val actualChange = actual[i]
            Assert.assertEquals("ordinal at $i not correct?", expected[i].ordinal, actualChange.ordinal)
            Assert.assertEquals("changeType at $i not correct?", expected[i].type, actualChange.type)
        }
    }
}
