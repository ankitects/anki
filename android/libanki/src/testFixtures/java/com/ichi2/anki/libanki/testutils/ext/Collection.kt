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

package com.ichi2.anki.libanki.testutils.ext

import anki.notetypes.StockNotetype
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note
import com.ichi2.anki.libanki.NotetypeJson
import com.ichi2.anki.libanki.addNotetypeLegacy
import com.ichi2.anki.libanki.backend.BackendUtils
import com.ichi2.anki.libanki.getStockNotetype

const val BASIC_NOTE_TYPE_NAME = "Basic"

fun Collection.addNote(note: Note): Int {
    addNote(note, note.notetype.did)
    return note.numberOfCards(this)
}

/**
 * Creates a basic model.
 *
 * Note: changes to this model will propagate to [createBasicTypingNoteType] as that model is built on
 * top of the model returned by this function.
 *
 * @param name name of the new model
 * @return the new model
 */
fun Collection.createBasicNoteType(name: String = BASIC_NOTE_TYPE_NAME): NotetypeJson {
    val noteType =
        getStockNotetype(StockNotetype.Kind.KIND_BASIC).apply { this.name = name }
    addNotetypeLegacy(BackendUtils.toJsonBytes(noteType))
    return notetypes.byName(name)!!
}

/**
 * Creates a basic typing model.
 *
 * @see createBasicNoteType
 */
fun Collection.createBasicTypingNoteType(name: String): NotetypeJson {
    val noteType = createBasicNoteType(name)
    noteType.templates[0].apply {
        qfmt = "{{Front}}\n\n{{type:Back}}"
        afmt = "{{Front}}\n\n<hr id=answer>\n\n{{type:Back}}"
    }
    notetypes.save(noteType)
    return noteType
}

/**
 * Return a new note with the model derived from the deck or the configuration
 * @param forDeck When true it uses the model specified in the deck (mid), otherwise it uses the model specified in
 * the configuration (curModel)
 * @return The new note
 */
fun Collection.newNote(forDeck: Boolean = true): Note = newNote(notetypes.current(forDeck))
