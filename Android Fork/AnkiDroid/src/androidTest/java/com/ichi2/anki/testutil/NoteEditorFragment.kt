// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.testutil

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import java.util.concurrent.atomic.AtomicReference

/**
 * Executes a block of code with the NoteEditor fragment on the activity's main thread.
 * @param block The block of code to execute with the NoteEditor fragment.
 * @throws Throwable if any exception is thrown during the execution of the block.
 */
@Throws(Throwable::class)
fun ActivityScenario<NoteEditorActivity>.onNoteEditor(block: (NoteEditorFragment) -> Unit) {
    val wrapped = AtomicReference<Throwable?>(null)
    this.onActivity { activity: NoteEditorActivity ->
        try {
            val editor: NoteEditorFragment = activity.getNoteEditorFragment()
            activity.runOnUiThread {
                try {
                    block(editor)
                } catch (t: Throwable) {
                    wrapped.set(t)
                }
            }
        } catch (t: Throwable) {
            wrapped.set(t)
        }
    }
    wrapped.get()?.let { throw it }
}

fun NoteEditorActivity.getNoteEditorFragment(): NoteEditorFragment =
    supportFragmentManager.findFragmentById(R.id.note_editor_fragment_frame) as NoteEditorFragment
