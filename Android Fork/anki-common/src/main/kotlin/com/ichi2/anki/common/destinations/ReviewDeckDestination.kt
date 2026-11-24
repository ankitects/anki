// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import android.content.Intent
import com.ichi2.anki.libanki.DeckId

/**
 * Opens the reviewer: either the legacy or the new reviewer, based on
 * `Prefs.isNewStudyScreenEnabled`.
 */
sealed class ReviewDeckDestination : Destination() {
    /**
     * Opens the reviewer for the currently selected deck.
     */
    data object CurrentDeck : ReviewDeckDestination()

    /**
     * Opens the reviewer for [deckId] via the app's entry point, for entry points outside the
     * app: widgets, shortcuts and reminders.
     *
     * @see Intent.FLAG_ACTIVITY_CLEAR_TOP
     */
    data class ExternalLaunch(
        val deckId: DeckId,
    ) : ReviewDeckDestination()
}
