/*
 * Copyright (c) 2022 Akshay Jadhav <jadhavakshay0701@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki

import androidx.fragment.app.Fragment
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Deck
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.model.SelectableDeck

// TODO left for this class:
//  - extract the TextView used as the deck name into own layout for easier reuse(note:
//    NoteEditorFragment usage looks slightly different)
//  - remove duplication in *.startDeckSelection() methods code
//  - rename(probably DeckSelection.kt or DeckSelectionUtil.kt) and move this class to a more
//    suitable package(probably com.ichi2.anki.utils)

/**
 * [DeckId] constant to represent "All decks" in screens that need it(ex. browser).
 */
const val ALL_DECKS_ID = 0L

/**
 * Returns the current selected deck only if it's not filtered otherwise returns the 'Default' deck.
 */
fun Collection.selectedDeckIfNotFiltered(): Deck {
    val selectedDeck = decks.getLegacy(decks.selected())
    return if (selectedDeck == null || selectedDeck.isFiltered) {
        decks.getDefault()
    } else {
        selectedDeck
    }
}

/**
 * Displays a [DeckSelectionDialog] for the user to select a deck, with the list of displayed decks
 * filtered based on the parameters of this method.
 * @param all true if 'All Decks' should be shown, false otherwise
 * @param filtered true if filtered decks should be shown, false otherwise
 * @param skipEmptyDefault true to hide the 'Default' deck if it doesn't have any cards, false to
 * show it anyway
 */
fun Fragment.startDeckSelection(
    all: Boolean = true,
    filtered: Boolean = true,
    skipEmptyDefault: Boolean = true,
) {
    requireActivity().launchCatchingTask {
        withProgress {
            val backendDecks =
                withCol {
                    decks.allNamesAndIds(includeFiltered = filtered, skipEmptyDefault = skipEmptyDefault)
                }
            val decks: MutableList<SelectableDeck> = backendDecks.map { SelectableDeck.Deck(it) }.toMutableList()
            if (all) {
                decks.add(0, SelectableDeck.AllDecks)
            }
            val dialog =
                DeckSelectionDialog.newInstance(
                    title = getString(R.string.select_deck),
                    decks = decks,
                )
            if (!parentFragmentManager.isStateSaved) {
                dialog.show(parentFragmentManager, "DeckSelectionDialog")
            }
        }
    }
}

/**
 * Displays a [DeckSelectionDialog] for the user to select a deck, with the list of displayed decks
 * filtered based on the parameters of this method.
 * @param all true if 'All Decks' should be shown, false otherwise
 * @param filtered true if filtered decks should be shown, false otherwise
 * @param skipEmptyDefault true to hide the 'Default' deck if it doesn't have any cards, false to
 * show it anyway
 */
fun AnkiActivity.startDeckSelection(
    all: Boolean = true,
    filtered: Boolean = true,
    skipEmptyDefault: Boolean = true,
) {
    launchCatchingTask {
        withProgress {
            val backendDecks =
                withCol {
                    decks.allNamesAndIds(includeFiltered = filtered, skipEmptyDefault = skipEmptyDefault)
                }
            val decks: MutableList<SelectableDeck> = backendDecks.map { SelectableDeck.Deck(it) }.toMutableList()
            if (all) {
                decks.add(0, SelectableDeck.AllDecks)
            }
            val dialog =
                DeckSelectionDialog.newInstance(
                    title = getString(R.string.select_deck),
                    decks = decks,
                )
            dialog.show(supportFragmentManager, "DeckSelectionDialog")
        }
    }
}
