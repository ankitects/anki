// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Note

// todo: duplicated & used in unit tests
fun Collection.addNote(note: Note): Int {
    addNote(note, note.notetype.did)
    return note.numberOfCards(this)
}
