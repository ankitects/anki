/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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
 *  this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.sched.CurrentQueueState

data class AnswerButtonsNextTime(
    val again: String,
    val hard: String,
    val good: String,
    val easy: String,
) {
    companion object {
        suspend fun from(state: CurrentQueueState): AnswerButtonsNextTime {
            val (again, hard, good, easy) = withCol { sched.describeNextStates(state.states) }
            return AnswerButtonsNextTime(again = again, hard = hard, good = good, easy = easy)
        }
    }
}
