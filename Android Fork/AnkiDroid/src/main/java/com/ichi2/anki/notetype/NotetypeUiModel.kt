// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import anki.notetypes.NotetypeNameId
import com.ichi2.anki.libanki.NoteTypeId

/**
 * Data holder class which contains the data to display a single note type in [AddNewNotesType]'s
 * list of notetypes.
 */
internal data class AddNotetypeUiModel(
    val id: NoteTypeId,
    val name: String,
    /**
     * Whether this is a note type provided by Anki by default.
     * If false, this is one of the note type currently in this collection (potentially a clone of a standard note type)
     */
    val isStandard: Boolean = false,
)

/**
 * A note type from current collection as a [AddNotetypeUiModel].
 */
internal fun NotetypeNameId.toUiModel(): AddNotetypeUiModel = AddNotetypeUiModel(id, name, false)
