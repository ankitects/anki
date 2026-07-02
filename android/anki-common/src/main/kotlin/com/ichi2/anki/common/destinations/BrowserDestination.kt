// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId

/** Opens the Card Browser. */
// TODO: A number of destination are undefined - grep for CardBrowser::class (#20558)
sealed class BrowserDestination : Destination() {
    /** Opens the Card Browser scoped to [deckId]. */
    data class ToDeck(
        val deckId: DeckId,
    ) : BrowserDestination()

    /**
     * Opens the Card Browser scoped to [deckId], auto-scrolling to [cardId]
     * if the card is present on the deck.
     */
    data class ScrollToCard(
        val deckId: DeckId,
        val cardId: CardId,
    ) : BrowserDestination()
}
