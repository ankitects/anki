/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
