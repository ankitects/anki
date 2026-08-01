/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.notetype

import android.content.Context
import android.content.Intent
import android.widget.Toast
import anki.notetypes.Notetype
import anki.notetypes.NotetypeNameIdUseCount
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CardTemplateEditor
import com.ichi2.anki.NoteTypeFieldEditor
import com.ichi2.anki.exception.ReportableException
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.utils.Destination

/** Encapsulates the entire state for [ManageNotetypes] */
data class ManageNoteTypesState(
    /** Indicator if the UI should show a "loading" view to the user */
    val isLoading: Boolean = true,
    /** List of [Notetype] to show */
    val noteTypes: List<NoteTypeItemState> = emptyList(),
    /** User entered string to use for filtering the [noteTypes] list */
    val searchQuery: String = "",
    /** Error that occurred or null for no error */
    val error: ReportableException? = null,
    /** Simple transient messages in response to user actions or null for no message */
    val message: UserMessage? = null,
    /**
     * If not null the user requested to go to this destination. Should to be handled immediately
     * and after marked as consumed.
     */
    val destination: Destination? = null,
    /**
     * Flag to indicate if we are selecting multiple items. This being true implies that at least
     * one item in the list of [com.ichi2.anki.notetype.NoteTypeItemState] has its isSelected
     * property set to true.
     */
    val isInMultiSelectMode: Boolean = false,
) {
    /** Simple message to be shown to the user, usually in a [Snackbar] or [Toast] */
    enum class UserMessage {
        /** Message to inform that the last [Notetype] can't be removed */
        DeletingLastModel,
    }

    data class CardEditor(
        val ntid: NoteTypeId,
    ) : Destination {
        override fun toIntent(context: Context) = CardTemplateEditor.getIntent(context, noteTypeId = ntid)
    }

    data class FieldsEditor(
        val ntid: NoteTypeId,
        val name: String,
    ) : Destination {
        override fun toIntent(context: Context): Intent =
            Intent(context, NoteTypeFieldEditor::class.java).apply {
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_NAME, name)
                putExtra(NoteTypeFieldEditor.EXTRA_NOTETYPE_ID, ntid)
            }
    }
}

/** Holds information about a single [Notetype] */
data class NoteTypeItemState(
    val id: NoteTypeId,
    val name: String,
    val useCount: Int,
    /**
     * Only set and used in multiple selection mode, true if this entry is currently selected,
     * false otherwise.
     */
    val isSelected: Boolean = false,
    /**
     * Flag to indicate if the ui should show this item or not, used for filtering items when we
     * want to hide entries but still holding on to them for their state.
     */
    val shouldBeDisplayed: Boolean = true,
) {
    companion object {
        fun asModel(source: NotetypeNameIdUseCount) = NoteTypeItemState(source.id, source.name, source.useCount)
    }
}
