// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import com.ichi2.anki.libanki.CardId
import com.ichi2.anki.libanki.DeckId

/** Opens the Card Browser. */
sealed class BrowserDestination : Destination() {
    /** Opens the Card Browser on the selected deck. */
    data object Open : BrowserDestination()

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

    /**
     * Opens the Card Browser, searching for [query].
     *
     * @param allDecks if `true`, search all decks instead of the selected deck
     */
    data class Search(
        val query: String,
        val allDecks: Boolean,
    ) : BrowserDestination()
}
