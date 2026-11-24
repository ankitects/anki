// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.deckpicker

import anki.collection.OpChangesWithCount
import anki.collection.copy
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.ext.remove
import timber.log.Timber

/**
 * Deletes a deck subtree and, when necessary, selects the nearest surviving visible deck.
 *
 * This always changes the collection's selected deck - on phones and in split-pane mode alike,
 * and the change syncs to other devices. This intentionally diverges from Anki Desktop, which
 * deletes the deck and lets the backend fall back to the Default deck: AnkiDroid's deck list
 * hides an empty Default, so selecting it would display a deck the user cannot see.
 * The selection change is merged into the deletion's undo entry, so a single Undo restores
 * both the deck and the selection.
 *
 * @param deckId the deck to remove, including its child decks
 */
context(col: Collection)
internal fun removeDeckAndSelectAdjacent(
    deckId: DeckId,
    visibleDeckIds: List<DeckId>,
    filter: DeckFilters,
): OpChangesWithCount {
    val deletedDeckIds = col.decks.deckAndChildIds(deckId).toSet()
    val selectedDeckWillBeDeleted = col.decks.selected() in deletedDeckIds
    val removal = col.decks.remove(deckId)
    if (!selectedDeckWillBeDeleted) return removal

    val deletionUndoStep = col.undoStatus().lastStep
    val replacementDeckId =
        nextVisibleDeckAfterDeletion(
            deletedDeckIds = deletedDeckIds,
            visibleDeckIds = visibleDeckIds,
            filter = filter,
        ) ?: col.decks
            .allNamesAndIds(skipEmptyDefault = true)
            .firstOrNull()
            ?.id ?: DEFAULT_DECK_ID
    Timber.i("Selecting deck %s after deleting selected deck %s", replacementDeckId, deckId)
    col.decks.select(replacementDeckId)

    return removal.copy { changes = col.mergeUndoEntries(deletionUndoStep) }
}

context(col: Collection)
private fun nextVisibleDeckAfterDeletion(
    deletedDeckIds: Set<DeckId>,
    visibleDeckIds: List<DeckId>,
    filter: DeckFilters,
): DeckId? {
    val lastDeletedIndex = visibleDeckIds.indexOfLast { it in deletedDeckIds }
    if (lastDeletedIndex == -1) return null

    val remainingVisibleIds =
        col.sched
            .deckDueTree()
            .filterAndFlattenDisplay(filter, selectedDeckId = DEFAULT_DECK_ID)
            .map { it.did }
            .toSet()

    return visibleDeckIds.drop(lastDeletedIndex + 1).firstOrNull { it in remainingVisibleIds }
        ?: visibleDeckIds.take(lastDeletedIndex).lastOrNull { it in remainingVisibleIds }
}
