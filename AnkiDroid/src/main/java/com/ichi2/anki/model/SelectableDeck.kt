/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.model

import android.content.Context
import android.os.Parcelable
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.ui.internationalization.sentenceCase
import kotlinx.parcelize.Parcelize

/**
 * Either a deck in the collection, or [AllDecks]
 */
sealed class SelectableDeck : Parcelable {
    @Parcelize
    data object AllDecks : SelectableDeck()

    @Parcelize
    data class Deck(
        val deckId: DeckId,
        val name: String,
    ) : SelectableDeck() {
        constructor(d: DeckNameId) : this(d.id, d.name)

        fun toDeckNameId() = DeckNameId(name = name, id = deckId)

        companion object {
            suspend fun fromId(id: DeckId): Deck = Deck(deckId = id, name = withCol { decks.name(id) })
        }
    }

    /**
     * The name to be displayed to the user. Contains only
     * the sub-deck name rather than the entire deck name.
     * Eg: foo::bar -> bar
     */
    fun getDisplayName(context: Context) =
        when (this) {
            is Deck -> name.substringAfterLast("::")
            is AllDecks -> with(context) { TR.sentenceCase.allDecks }
        }

    /**
     * The full name of the deck
     */
    fun getFullDisplayName(context: Context) =
        when (this) {
            is Deck -> name
            is AllDecks -> with(context) { TR.sentenceCase.allDecks }
        }

    override fun toString() =
        when (this) {
            is Deck -> name
            is AllDecks -> "All Decks"
        }

    companion object {
        /**
         * @param includeFiltered Whether to include filtered decks in the output
         * @return all [SelectableDecks][SelectableDeck] in the collection satisfying the filter
         */
        suspend fun fromCollection(includeFiltered: Boolean): List<Deck> =
            CollectionManager
                .withCol { decks.allNamesAndIds(includeFiltered = includeFiltered) }
                .map { nameAndId -> Deck(nameAndId) }
    }
}
