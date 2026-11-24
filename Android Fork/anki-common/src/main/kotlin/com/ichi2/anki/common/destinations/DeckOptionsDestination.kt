// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.destinations

import androidx.annotation.CheckResult
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId

/**
 * @param options the list of deck options to present to the user before going to deck options. This
 * will contain the current selected deck([deckId]) plus any other possible deck targets(ex: decks
 * of the current studied card)
 */
data class DeckOptionsDestination(
    val deckId: DeckId,
    val isFiltered: Boolean,
    val options: List<DeckOptionsEntry> = emptyList(),
) : Destination() {
    companion object {
        suspend fun fromDeckId(deckId: DeckId): DeckOptionsDestination =
            DeckOptionsDestination(
                deckId = deckId,
                isFiltered = withCol { decks.isFiltered(deckId) },
            )

        @CheckResult
        suspend fun fromCurrentDeck(): DeckOptionsDestination =
            withCol {
                val deckId = decks.getCurrentId()
                DeckOptionsDestination(
                    deckId = deckId,
                    isFiltered = decks.isFiltered(deckId),
                )
            }
    }
}

/**
 * Information about a deck that appears in the list of possible deck targets when deck options are
 * requested from the study screen.
 */
data class DeckOptionsEntry(
    val deckId: DeckId,
    val name: String?,
    val isFiltered: Boolean,
)
