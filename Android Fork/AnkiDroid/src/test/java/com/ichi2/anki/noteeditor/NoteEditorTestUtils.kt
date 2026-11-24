// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.noteeditor

import android.os.Bundle
import com.ichi2.anki.NoteEditorActivity
import com.ichi2.anki.NoteEditorFragment
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.annotations.DuplicatedCode
import com.ichi2.anki.common.destinations.NoteEditorDestination

/**
 * Hosts a [NoteEditorActivity] with the given launcher [arguments] and returns the
 * embedded [NoteEditorFragment]. Shared between tests that need to drive note-editor
 * flows without re-implementing the activity setup.
 */
fun RobolectricTest.openNoteEditorWithArgs(
    arguments: Bundle,
    action: String? = null,
): NoteEditorFragment {
    val activity =
        startActivityNormallyOpenCollectionWithIntent(
            NoteEditorActivity::class.java,
            NoteEditorDestination.PassArguments(arguments, action).toIntent(targetContext),
        )
    return activity.getNoteEditorFragment()
}

@DuplicatedCode("NoteEditor in androidTest")
fun NoteEditorActivity.getNoteEditorFragment(): NoteEditorFragment =
    supportFragmentManager.findFragmentById(R.id.note_editor_fragment_frame) as NoteEditorFragment
