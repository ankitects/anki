// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.servicelayer

import androidx.fragment.app.FragmentActivity
import com.google.android.material.snackbar.Snackbar
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonPlurals
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
            CommonPlurals.reset_cards_dialog_acknowledge,
            count,
            count,
        ),
        Snackbar.LENGTH_SHORT,
    )
}
