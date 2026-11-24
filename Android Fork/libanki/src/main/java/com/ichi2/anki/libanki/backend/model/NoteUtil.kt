// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.backend.model

import anki.notes.note
import com.ichi2.anki.libanki.Note

fun Note.toBackendNote(): anki.notes.Note {
    val note = this
    return note {
        id = note.id
        guid = note.guId!!
        notetypeId = note.noteTypeId
        mtimeSecs = note.mod
        usn = note.usn
        tags.addAll(note.tags.asIterable())
        fields.addAll(note.fields.asIterable())
    }
}
