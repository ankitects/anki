// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Vaibhavi Lokegaonkar <vaibhavilokegaonkar@gmail.com> Github Username: Vaibhavi1707

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertFalse
import junit.framework.TestCase.assertTrue
import org.junit.Assert.assertNotEquals
import org.junit.Ignore
import org.junit.Test

class TagsTest : InMemoryAnkiTest() {
    @Test
    @Ignore("Upstream Anki Issue 5863: https://github.com/ankitects/anki/issues/5863")
    fun `backend preserves distinct ligature tags`() =
        runTest {
            val note = addBasicNote()

            col.backend.addNoteTags(noteIds = listOf(note.id), tags = "iff ﬃ")

            assertEquals(setOf("iff", "ﬃ"), col.getNote(note.id).tags.toSet())
        }

    @Test
    @Ignore("Upstream Anki Issue 5963: https://github.com/ankitects/anki/issues/5963")
    fun `backend merges newer Unicode case pairs`() =
        runTest {
            val note = addBasicNote()

            col.backend.addNoteTags(noteIds = listOf(note.id), tags = "\ua7d0 \ua7d1")

            assertEquals(listOf("\ua7d0"), col.getNote(note.id).tags)
        }

    @Test
    @Ignore("Upstream Anki Issue 5964: https://github.com/ankitects/anki/issues/5964")
    fun `backend renames every spelling of a tag subtree`() =
        runTest {
            val note = addBasicNote()
            col.backend.addNoteTags(noteIds = listOf(note.id), tags = "Straße::a STRASSE::b")

            col.backend.renameTags(currentPrefix = "Straße", newPrefix = "target")

            assertEquals(setOf("target::a", "target::b"), col.getNote(note.id).tags.toSet())
        }

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
