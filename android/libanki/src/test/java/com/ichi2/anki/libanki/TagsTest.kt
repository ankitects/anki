/*
 *  Copyright (c) 2021 Vaibhavi Lokegaonkar <vaibhavilokegaonkar@gmail.com> Github Username: Vaibhavi1707
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Test

class TagsTest : InMemoryAnkiTest() {
    @Test
    fun test_split() {
        val tags = Tags(col)
        val tagsList1 = ArrayList<String>()
        tagsList1.add("Todo")
        tagsList1.add("todo")
        tagsList1.add("Needs revision")

        val tagsList2 = ArrayList<String>()
        tagsList2.add("Todo")
        tagsList2.add("todo")
        tagsList2.add("Needs")
        tagsList2.add("Revision")

        assertNotEquals(tagsList1, tags.split("Todo todo Needs Revision"))
        assertEquals(tagsList2, tags.split("Todo todo Needs Revision"))
        assertEquals(0, tags.split("").size)
    }

    @Test
    fun test_in_list() {
        val tags = Tags(col)

        val tagsList = ArrayList<String>()
        tagsList.add("Todo")
        tagsList.add("Needs revision")
        tagsList.add("Once more")
        tagsList.add("test1 content")

        assertFalse(tags.inList("Done", tagsList))
        assertTrue(tags.inList("Needs revision", tagsList))
        assertTrue(tags.inList("once More", tagsList))
        assertFalse(tags.inList("test1Content", tagsList))
        assertFalse(tags.inList("", ArrayList()))
    }
}
