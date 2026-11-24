// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Ankitects Pty Ltd <https://apps.ankiweb.net>                    *

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
    @NotInPyLib
    val undoAvailable: Boolean
        get() = undo != null

    @NotInPyLib
    val redoAvailable: Boolean
        get() = redo != null

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
fun Collection.undoAvailable(): Boolean = undoStatus().undoAvailable

@NotInPyLib
fun Collection.redoLabel(): String? {
    val action = undoStatus().redo
    return action?.let { tr.undoRedoAction(it) }
}

@NotInPyLib
fun Collection.redoAvailable(): Boolean = undoStatus().redoAvailable
