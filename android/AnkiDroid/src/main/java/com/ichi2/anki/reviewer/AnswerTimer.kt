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

package com.ichi2.anki.reviewer

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.widget.Chronometer
import androidx.annotation.MainThread
import androidx.annotation.VisibleForTesting
import androidx.core.view.isInvisible
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.R
import com.ichi2.anki.libanki.Card
import com.ichi2.anki.libanki.Collection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Responsible for pause/resume of the card timer and the UI element displaying the amount of time to answer a card
 *
 * This is the time to answer the question, and to view the answer
 *
 * Handled by the "Show answer timer" and "Maximal answer time" in the deck options.
 *
 * This is not responsible for the initial call to [Card.startTimer], but does handle this on pause/resume
 *
 * @see [Card.timeTaken] - used by the scheduler
 */
class AnswerTimer(
    private val cardTimer: Chronometer,
) {
    @VisibleForTesting
    var limit: Int = 0
        private set

    private lateinit var currentCard: Card

    private val context: Context
        get() = cardTimer.context

    var showTimer: Boolean = false
        private set

    /**
     * Changes the timer visibility based on [Card.shouldShowTimer],
     * resets the timer to an initial state and starts it
     *
     * This may also change the limit, based on [Card.timeLimit]
     */
    @MainThread // resetTimerUI
    fun setupForCard(
        col: Collection,
        newCard: Card,
    ) {
        currentCard = newCard
        showTimer = newCard.shouldShowTimer(col)
        if (showTimer && cardTimer.isInvisible) {
            cardTimer.visibility = View.VISIBLE
        } else if (!showTimer && cardTimer.visibility != View.INVISIBLE) {
            cardTimer.visibility = View.INVISIBLE
        }

        // Optimisation to remove the number of timers. Speedup at call site is negligible (~500 micros)
        if (!showTimer) {
            cardTimer.stop()
        } else {
            resetTimerUI(col, newCard)
        }
    }

    @MainThread // cardTimer.base
    private fun resetTimerUI(col: Collection, newCard: Card) {
        // Set normal timer color
        cardTimer.setTextColor(MaterialColors.getColor(context, android.R.attr.textColor, 0))

        cardTimer.base = elapsedRealTime
        cardTimer.start()

        // Stop and highlight the timer if it reaches the time limit.
        limit = newCard.timeLimit(col)
        cardTimer.setOnChronometerTickListener { chronometer: Chronometer ->
            val elapsed: Long = elapsedRealTime - chronometer.base
            if (elapsed >= limit) {
                chronometer.setTextColor(MaterialColors.getColor(context, R.attr.maxTimerColor, 0))
                chronometer.stop()
            }
        }
    }

    fun pause() {
        if (!this::currentCard.isInitialized) {
            return
        }

        // We stop the UI timer so it doesn't trigger the tick listener while paused. Letting
        // it run would trigger the time limit condition (red, stopped timer) in the background.
        cardTimer.stop()
        currentCard.stopTimer()
    }

    suspend fun resume() {
        if (!this::currentCard.isInitialized) {
            return
        }
        // Resume the card timer first. It internally accounts for the time gap between
        // suspend and resume.
        currentCard.resumeTimer()

        if (!showTimer) {
            return
        }
        // Then update and resume the UI timer. Set the base time as if the timer had started
        // timeTaken() seconds ago.
        setBase(elapsedRealTime - withCol { currentCard.timeTaken(this@withCol) })

        // Don't start the timer if we have already reached the time limit or it will tick over
        if (elapsedRealTime - cardTimer.base < withCol { currentCard.timeLimit(this@withCol) }) {
            cardTimer.start()
        }
    }

    fun setVisibility(visibility: Int) {
        if (!showTimer) {
            return
        }

        cardTimer.visibility = visibility
    }

    /** Milliseconds since boot */
    private val elapsedRealTime
        get() = SystemClock.elapsedRealtime()

    /** @see Chronometer.setBase */
    private suspend fun setBase(base: Long) = withContext(Dispatchers.Main) { cardTimer.base = base }
}
