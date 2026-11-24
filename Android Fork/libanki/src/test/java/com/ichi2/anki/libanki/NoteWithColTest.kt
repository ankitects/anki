// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.testutils.ext.newNote
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class NoteWithColTest : InMemoryAnkiTest() {
    @Test
    fun newNoteTest() {
        val note = col.newNote()
        assertThat(note.notetype.name, equalTo("Basic"))
    }
}
