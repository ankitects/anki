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

import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.Deck

// TODO left for this class:
//  - extract the TextView used as the deck name into own layout for easier reuse(note:
//    NoteEditorFragment usage looks slightly different)
//  - rename(probably DeckSelection.kt or DeckSelectionUtil.kt) and move this class to a more
//    suitable package(probably com.ichi2.anki.utils)

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
