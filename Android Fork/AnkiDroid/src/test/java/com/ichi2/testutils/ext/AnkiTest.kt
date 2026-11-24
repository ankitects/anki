// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils.ext

import anki.collection.OpChangesWithCount
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.QueueType
import com.ichi2.anki.libanki.testutils.AnkiTest
import com.ichi2.anki.observability.undoableOp

/** Corrupts the deck hierarchy so the next scheduler queue/count fetch throws "deck not found in limits map". */
fun AnkiTest.triggerDeckNotFoundInLimitsMap() {
    addDeck("A::B::C").withNote(QueueType.New)
    val parentDeckId = col.decks.idForName("A::B")!!
    // Drop A::B without removing A::B::C, leaving it without an entry in the limits map.
    col.db.execute("delete from decks where id = ?", parentDeckId)
    col.decks.select(col.decks.idForName("A")!!)
}

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
