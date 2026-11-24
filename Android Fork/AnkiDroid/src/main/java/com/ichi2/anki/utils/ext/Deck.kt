// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.Decks

fun Decks.update(
    did: DeckId,
    block: Deck.() -> Unit,
) {
    val deck = getLegacy(did)!!
    block(deck)
    this.save(deck)
}

/** @see Decks.remove */
fun Decks.remove(deckId: DeckId) = remove(listOf(deckId))
