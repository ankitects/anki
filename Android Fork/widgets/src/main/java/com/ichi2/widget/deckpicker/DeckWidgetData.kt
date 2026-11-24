// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Anoop <xenonnn4w@gmail.com>

package com.ichi2.widget.deckpicker

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId

/**
 * Data class representing the data for a deck displayed in the widget.
 *
 * @property deckId The ID of the deck.
 * @property name The name of the deck.
 * @property reviewCount The number of cards due for review.
 * @property learnCount The number of cards in the learning phase.
 * @property newCount The number of new cards.
 */
data class DeckWidgetData(
    val deckId: DeckId,
    val name: String,
    val reviewCount: Int,
    val learnCount: Int,
    val newCount: Int,
)

/**
 * Map deck id to the associated DeckPickerWidgetData. Omits any id that does not correspond to a deck.
 *
 * Note: This operation may be slow, as it involves processing the entire deck collection.
 *
 * @param deckId the list of deck ID to retrieve data for
 * @return a list of DeckPickerWidgetData objects containing deck names and statistics
 */
suspend fun getDeckNameAndStats(deckId: DeckId): DeckWidgetData? = getDeckNamesAndStats(listOf(deckId)).getOrNull(0)

suspend fun getDeckNamesAndStats(deckIds: List<DeckId>): List<DeckWidgetData> {
    val result = mutableListOf<DeckWidgetData>()

    val deckTree = withCol { sched.deckDueTree() }

    deckTree.forEach { node ->
        if (node.did !in deckIds) return@forEach
        result.add(
            DeckWidgetData(
                deckId = node.did,
                name = node.lastDeckNameComponent,
                reviewCount = node.revCount,
                learnCount = node.lrnCount,
                newCount = node.newCount,
            ),
        )
    }

    val deckIdToData = result.associateBy { it.deckId }
    return deckIds.mapNotNull { deckIdToData[it] }
}
