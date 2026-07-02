//noinspection MissingCopyrightHeader #8659
package com.ichi2.anki

import com.ichi2.anki.libanki.Card

/**
 * Utilities for working on multiple cards
 */
object CardUtils {
    /**
     * Returns the deck ID of the given [Card].
     *
     * @param card The [Card] to get the deck ID
     * @return The deck ID of the [Card]
     */
    fun getDeckIdForCard(card: Card): Long {
        // Try to get the configuration by the original deck ID (available in case of a cram deck),
        // else use the direct deck ID (in case of a 'normal' deck.
        return if (card.oDid == 0L) card.did else card.oDid
    }
}
