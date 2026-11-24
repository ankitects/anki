// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.tags

import anki.collection.OpChangesAfterUndo
import anki.collection.OpChangesWithCount
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.UndoStatus
import com.ichi2.anki.libanki.redoLabel
import com.ichi2.anki.libanki.undoLabel
import timber.log.Timber

/**
 * Limits undo/redo to this screen's changes until another caller changes shared history.
 * Access only inside [withCol], including when recording local operations.
 */
internal class ManageTagsUndoHistory {
    private var undoCount = 0
    private var redoCount = 0
    private var expectedUndoStatus: UndoStatus? = null

    /**
     * Allows undo/redo to reach history from before this Manage Tags screen opened.
     *
     * Keeping this `false` is the better UX: undo stops at the screen's opening boundary,
     * protecting work done before opening it. If another caller changes shared history,
     * the local counts become unreliable. We then allow earlier history so interleaved
     * API actions remain undoable, even though the opening boundary can no longer be enforced.
     *
     * TODO: Preserve the opening boundary even with interleaved API actions.
     */
    private var allowEarlierHistory = false

    /**
     * Undoes the last change, respecting this screen's history boundary when possible.
     * @see Collection.undo
     */
    fun undo(col: Collection): OpChangesAfterUndo {
        val status = checkHistory(col)
        if (!status.undoAvailable || (undoCount == 0 && !allowEarlierHistory)) {
            return OpChangesAfterUndo.getDefaultInstance()
        }
        return col.undo().also {
            if (!allowEarlierHistory) {
                undoCount--
                redoCount++
            }
            expectedUndoStatus = col.undoStatus()
        }
    }

    /**
     * Redoes the last undone change, respecting this screen's history boundary when possible.
     * @see Collection.redo
     */
    fun redo(col: Collection): OpChangesAfterUndo {
        val status = checkHistory(col)
        if (!status.redoAvailable || (redoCount == 0 && !allowEarlierHistory)) {
            return OpChangesAfterUndo.getDefaultInstance()
        }
        return col.redo().also {
            if (!allowEarlierHistory) {
                redoCount--
                undoCount++
            }
            expectedUndoStatus = col.undoStatus()
        }
    }

    /**
     * Runs [block] and records actual undo entries within this screen's history.
     *
     * For example, `trackTagChange(col) { tags.remove("unused") }` counts deletion from the
     * tag cache even when no notes were affected.
     *
     * No-op operations still refresh [expectedUndoStatus], since they can advance
     * [UndoStatus.lastStep] without adding an undo entry.
     */
    fun trackTagChange(
        col: Collection,
        block: Collection.() -> OpChangesWithCount,
    ): OpChangesWithCount {
        checkHistory(col)
        return col.block().also {
            if (it.changes.mtime && !allowEarlierHistory) {
                undoCount++
                redoCount = 0
            }
            // No-op tag operations can advance lastStep without creating an undo entry.
            expectedUndoStatus = col.undoStatus()
        }
    }

    /**
     * Records a local change to backend history; [block] must not add an undo entry.
     *
     * For example, `trackNonUndoableChange(col) { tags.setCollapsed("parent", true) }` saves an
     * expansion preference. This advances [UndoStatus.lastStep] and clears redo without adding
     * an undo entry.
     */
    fun <T> trackNonUndoableChange(
        col: Collection,
        block: Collection.() -> T,
    ): T {
        checkHistory(col)
        return col.block().also {
            // Expansion preferences change the backend counter without adding an undo step.
            expectedUndoStatus = col.undoStatus()
        }
    }

    /**
     * Localized undo/redo labels, or null when the corresponding action is unavailable.
     * @see UndoRedoLabels
     */
    fun labels(col: Collection): UndoRedoLabels {
        checkHistory(col)
        return UndoRedoLabels(
            undoLabel = col.undoLabel().takeIf { allowEarlierHistory || undoCount > 0 },
            redoLabel = col.redoLabel().takeIf { allowEarlierHistory || redoCount > 0 },
        )
    }

    /**
     * Returns the current history snapshot, clearing counts for unavailable undo/redo actions.
     *
     * After the first snapshot, an unexpected status change enables [allowEarlierHistory]:
     * interleaved changes (for example, API note updates) make the opening boundary unreliable.
     */
    private fun checkHistory(col: Collection): UndoStatus {
        val current = col.undoStatus()
        if (!allowEarlierHistory && expectedUndoStatus != null && current != expectedUndoStatus) {
            allowEarlierHistory = true
            Timber.w(
                "Undo history changed outside Manage Tags; allowing undo before this screen opened (expected=%s, current=%s)",
                expectedUndoStatus,
                current,
            )
        }
        if (!current.undoAvailable) undoCount = 0
        if (!current.redoAvailable) redoCount = 0
        expectedUndoStatus = current
        return current
    }
}

/** Localized action labels; null means the corresponding action is unavailable. */
internal data class UndoRedoLabels(
    val undoLabel: String?,
    val redoLabel: String?,
)
