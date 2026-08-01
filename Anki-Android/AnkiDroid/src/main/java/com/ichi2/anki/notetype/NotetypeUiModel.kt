/*
 * Copyright (c) 2022 lukstbit <52494258+lukstbit@users.noreply.github.com>
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
