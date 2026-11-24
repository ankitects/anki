// SPDX-License-Identifier: GPL-3.0-or-later

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
