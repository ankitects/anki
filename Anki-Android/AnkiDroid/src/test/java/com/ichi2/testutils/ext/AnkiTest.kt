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

package com.ichi2.testutils.ext

import anki.collection.OpChangesWithCount
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.observability.undoableOp

suspend fun AnkiTest.addBasicNoteWithOp(
    fields: List<String> = listOf("foo", "bar"),
    noteType: NotetypeJson = col.notetypes.byName("Basic")!!,
): Note =
    col.newNote(noteType).also { note ->
        for ((i, field) in fields.withIndex()) {
            note.setField(i, field)
        }
        undoableOp<OpChangesWithCount> { col.addNote(note, Consts.DEFAULT_DECK_ID) }
    }
