// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.timebudget

import android.content.Context
import anki.scheduler.TimeBudgetStatusResponse
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.sched.DeckNode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.math.ceil

/**
 * What the deck list shows instead of the card counts for a deck studied with a time budget.
 *
 * @param remainingSecs negative once the budget is used up
 * @param done nothing is left to study today
 * @param inherited a subdeck studied with its parent's budget: shows nothing
 */
data class TimeBudgetBadge(
    val remainingSecs: Int,
    val done: Boolean,
    val inherited: Boolean = false,
)

/**
 * Remaining study time of decks with an RWKV-Instant time budget.
 *
 * [checkDone] also works out whether each deck has anything left to study. That needs the RWKV
 * model and may replay the review log first, so only use it in the background.
 */
fun Collection.timeBudgetStatus(checkDone: Boolean = false): Map<DeckId, TimeBudgetStatusResponse.Deck> =
    backend.timeBudgetStatus(checkDone = checkDone).associateBy { it.deckId }

/** Remaining seconds of the budget covering [deckId], if any. */
fun Collection.remainingTimeBudgetSecs(deckId: DeckId): Int? {
    val remaining = timeBudgetStatus()
    if (remaining.isEmpty()) return null
    val deck = decks.getLegacy(deckId) ?: return null
    if (deck.isFiltered) return null
    val candidates = listOf(deckId) + decks.parents(deckId).reversed().map { it.id }
    return candidates.firstNotNullOfOrNull { remaining[it]?.remainingSecs }
}

/**
 * The badge for each deck in [this] tree covered by a budget: decks with their own budget, and
 * their (non-filtered) subdecks, which are studied with it.
 */
fun DeckNode.timeBudgetBadges(status: Map<DeckId, TimeBudgetStatusResponse.Deck>): Map<DeckId, TimeBudgetBadge> {
    val badges = mutableMapOf<DeckId, TimeBudgetBadge>()
    if (status.isEmpty()) return badges

    fun visit(
        node: DeckNode,
        inBudget: Boolean,
    ) {
        var childrenInBudget = inBudget
        if (!node.filtered) {
            val own = status[node.did]
            if (own != null) {
                badges[node.did] = TimeBudgetBadge(own.remainingSecs, own.done)
                childrenInBudget = true
            } else if (inBudget) {
                badges[node.did] = TimeBudgetBadge(0, done = false, inherited = true)
            }
        }
        for (child in node.children) {
            visit(child, childrenInBudget)
        }
    }
    visit(this, inBudget = false)
    return badges
}

/** "12 min" for the time left (never negative). */
fun formatRemaining(
    context: Context,
    remainingSecs: Int,
): String {
    val minutes = ceil(remainingSecs.coerceAtLeast(0) / 60.0).toInt()
    return context.getString(R.string.time_budget_minutes_left, minutes)
}

/** A checkmark once nothing is left to study today, else the time left. */
fun formatBadge(
    context: Context,
    badge: TimeBudgetBadge,
): String =
    when {
        badge.inherited -> ""
        badge.done -> DONE_MARK
        else -> formatRemaining(context, badge.remainingSecs)
    }

const val DONE_MARK = "✅"

/**
 * Scores the next batch of a time-budget session while the question is on screen, so the next
 * card rarely waits for it. A no-op in the backend unless the current batch is running low.
 * Errors are only logged: this is an optimisation, and the batch is refilled on demand.
 */
fun CoroutineScope.prefetchTimeBudgetCards() {
    launch(Dispatchers.IO) {
        try {
            withCol { backend.prefetchTimeBudgetCards() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "prefetchTimeBudgetCards")
        }
    }
}
