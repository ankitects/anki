/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
import net.ankiweb.rsdroid.exceptions.BackendInvalidInputException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import kotlin.test.assertFailsWith

class NotetypesTest : InMemoryAnkiTest() {
    @Test
    fun `getSingleNotetypeOfNotes - multiple`() {
        val notes = addNotes(2)
        val result = col.notetypes.getSingleNotetypeOfNotes(notes.map { it.id })
        assertThat(result, equalTo(notes.first().notetype.id))
    }

    @Test
    fun `getSingleNotetypeOfNotes - valid`() {
        val note = addNotes(1).single()
        val result = col.notetypes.getSingleNotetypeOfNotes(listOf(note.id))
        assertThat(result, equalTo(note.notetype.id))
    }

    @Test
    fun `getSingleNotetypeOfNotes - no input`() {
        val result = assertFailsWith<BackendInvalidInputException> { col.notetypes.getSingleNotetypeOfNotes(emptyList()) }
        assertThat(result.message, equalTo("no note id provided"))
    }

    @Test
    fun `getSingleNotetypeOfNotes - invalid input`() {
        val noteIds = listOf<Long>(1)
        val result = assertFailsWith<BackendNotFoundException> { col.notetypes.getSingleNotetypeOfNotes(noteIds) }
        assertThat(
            result.message,
            equalTo("Your database appears to be in an inconsistent state. Please use the Check Database action. No such note: '1'"),
        )
    }

    @Test
    fun `getSingleNotetypeOfNotes - one invalid`() {
        val noteIds = listOf(1, addNotes(1).single().id)
        val result = assertFailsWith<BackendNotFoundException> { col.notetypes.getSingleNotetypeOfNotes(noteIds) }
        assertThat(
            result.message,
            equalTo("Your database appears to be in an inconsistent state. Please use the Check Database action. No such note: '1'"),
        )
    }

    @Test
    fun `getSingleNotetypeOfNotes - mixed`() {
        val basicNote = addNotes(1).single()
        val clozeNote = addClozeNote("{{c1::aa}}")
        val result =
            assertFailsWith<BackendInvalidInputException> { col.notetypes.getSingleNotetypeOfNotes(listOf(basicNote.id, clozeNote.id)) }
        assertThat(result.message, equalTo("Please select notes from only one note type."))
    }
}
