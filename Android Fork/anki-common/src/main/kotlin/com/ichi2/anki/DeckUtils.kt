// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts
import com.ichi2.anki.libanki.DeckId

/**
 * Checks if a given deck, including its subdecks if specified, is empty.
 *
 * @param deckId The ID of the deck to check.
 * @param includeSubdecks If true, includes subdecks in the check. Default is true.
 * @return `true` if the deck (and subdecks if specified) is empty, otherwise `false`.
 */
private fun Collection.isDeckEmpty(
    deckId: DeckId,
    includeSubdecks: Boolean = true,
): Boolean {
    val deckIds = decks.deckAndChildIds(deckId)
    val totalCardCount = decks.cardCount(*deckIds.toLongArray(), includeSubdecks = includeSubdecks)
    return totalCardCount == 0
}

/**
 * Checks if the default deck is empty.
 *
 * This method runs on an IO thread and accesses the collection to determine if the default deck (with ID 1) is empty.
 *
 * @return `true` if the default deck is empty, otherwise `false`.
 */
suspend fun isDefaultDeckEmpty(): Boolean = withCol { isDeckEmpty(Consts.DEFAULT_DECK_ID) }

/**
 * Checks if the deck with the specified ID is accessible to the user. For most decks, a deck is viewable
 * and editable by the user simply if it exists. However, the default deck (ID=1) is always present in the collection
 * but is hidden from the user when it is empty. Therefore, for the default deck, we check if it is empty.
 */
suspend fun canUserAccessDeck(did: DeckId): Boolean =
    when (did) {
        Consts.DEFAULT_DECK_ID -> !isDefaultDeckEmpty()
        else -> withCol { decks.have(did) }
    }

/**
 * Returns whether the deck picker displays any deck.
 * Technically, it means that there is a non-default deck, or that the default deck is non-empty.
 *
 * This function is specifically implemented to address an issue where the default deck
 * isn't handled correctly when a second deck is added to the
 * collection. In this case, the deck tree may incorrectly appear as non-empty when it contains
 * only the default deck and no other cards.
 *
 */
suspend fun isCollectionEmpty(): Boolean {
    val tree = withCol { sched.deckDueTree() }
    val onlyDefaultDeckAvailable = tree.children.singleOrNull()?.did == Consts.DEFAULT_DECK_ID
    return onlyDefaultDeckAvailable && isDefaultDeckEmpty()
}
