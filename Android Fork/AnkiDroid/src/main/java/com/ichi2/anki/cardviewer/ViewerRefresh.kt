// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import anki.collection.OpChanges

/** Data for a deferred refresh of the CardViewer */
data class ViewerRefresh(
    val queues: Boolean,
    val note: Boolean,
    val card: Boolean,
) {
    companion object {
        /** updates the current state of the ViewerRefresh with additional data */
        fun updateState(
            currentState: ViewerRefresh?,
            changes: OpChanges,
        ): ViewerRefresh? {
            if (!changes.studyQueues && !changes.noteText && !changes.card) return currentState
            return ViewerRefresh(
                queues = changes.studyQueues || currentState?.queues == true,
                note = changes.noteText || currentState?.note == true,
                card = changes.card || currentState?.card == true,
            )
        }
    }
}
