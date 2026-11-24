// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>

package com.ichi2.anki.noteeditor

/**
 * A class can listen to update in the note editor by implementing this interface and setting itself as the fragment's delegate
 */
interface NoteEditorFragmentDelegate {
    /**
     * Called when the note editor is ready to be displayed
     */
    fun onNoteEditorReady()

    /**
     * Called when any change is made to the note being edited (fields, tags, etc)
     */
    fun onNoteTextChanged()

    /**
     * Called when the note is saved
     */
    fun onNoteSaved()

    /**
     * Called when the note type is changed
     */
    fun onNoteTypeChanged()
}
