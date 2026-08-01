/*
 * Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                    *
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

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.RustCleanup
import anki.collection.UndoStatus as UndoStatusProto

/**
 * If undo/redo available, a localized string describing the action will be set.
 */
data class UndoStatus(
    val undo: String?,
    val redo: String?,
    // not currently used
    val lastStep: UndoStepCounter,
) {
    companion object {
        fun from(proto: UndoStatusProto): UndoStatus =
            UndoStatus(
                undo = proto.undo.ifEmpty { null },
                redo = proto.redo.ifEmpty { null },
                lastStep = proto.lastStep,
            )
    }
}

/** eg "Undo suspend card" if undo available */
@NotInPyLib
@RustCleanup("similar to deprecated 'undo_name'")
fun Collection.undoLabel(): String? {
    val action = undoStatus().undo
    return action?.let { tr.undoUndoAction(it) }
}

@NotInPyLib
fun Collection.undoAvailable(): Boolean {
    val status = undoStatus()
    return status.undo != null
}

@NotInPyLib
fun Collection.redoLabel(): String? {
    val action = undoStatus().redo
    return action?.let { tr.undoRedoAction(it) }
}

@NotInPyLib
fun Collection.redoAvailable(): Boolean = undoStatus().redo != null
