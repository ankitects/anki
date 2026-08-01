/*
 *  Copyright (c) 2025 Hari Srinivasan <harisrini21@gmail.com>
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
