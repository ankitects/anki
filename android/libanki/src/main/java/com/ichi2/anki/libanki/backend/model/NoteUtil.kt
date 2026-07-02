/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
