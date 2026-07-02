/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.servicelayer

import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.observability.undoableOp
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.withProgress

suspend fun FragmentActivity.rescheduleCards(
    cardIds: List<CardId>,
    newDays: Int,
) {
    withProgress {
        undoableOp {
            sched.reschedCards(cardIds, newDays, newDays)
        }
    }
    val count = cardIds.size
    showSnackbar(TR.schedulingSetDueDateDone(count), Snackbar.LENGTH_SHORT)
}

suspend fun FragmentActivity.resetCards(
    cardIds: List<CardId>,
    restorePosition: Boolean = false,
    resetCounts: Boolean = false,
) {
    withProgress {
        undoableOp {
            sched.forgetCards(cardIds, restorePosition, resetCounts)
        }
    }
    val count = cardIds.size
    showSnackbar(
        resources.getQuantityString(
            R.plurals.reset_cards_dialog_acknowledge,
            count,
            count,
        ),
        Snackbar.LENGTH_SHORT,
    )
}
