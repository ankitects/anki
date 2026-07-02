/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.browser

import anki.search.BrowserColumns
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS

/**
 * A column available in the [browser][CardBrowser]
 *
 * @see [anki.search.BrowserRow] for data associated with a column
 *
 * @param ankiColumnKey The key used in [Backend.setActiveBrowserColumns]
 */
enum class CardBrowserColumn(
    val ankiColumnKey: String,
) {
    /** Rendered front side of the first card of the note */
    QUESTION("question"),

    /** Rendered back side of the first card of the note */
    ANSWER("answer"),

    /** The value of the field marked as "Sort by this field in the Browser" */
    SFLD("noteFld"),

    /**
     * Cards -> The deck which contains the card
     * Notes -> Either the deck containing the card, or `(n)`, where n is the number of
     * distinct decks
     */
    DECK("deck"),

    /** A list of tags for the note */
    TAGS("noteTags"),

    /**
     * Cards -> Card Type
     * Notes -> Cards (card count)
     */
    CARD("template"),
    DUE("cardDue"),

    /**
     * Cards -> Ease
     * Notes -> Average Ease
     */
    EASE("cardEase"),

    /**
     * Cards -> Timestamp the card was modified
     * Notes -> Most recent timestamp a card of the note was modified
     */
    CHANGED("cardMod"),

    /**
     * Timestamp the note was created
     */
    CREATED("noteCrt"),

    /**
     * Timestamp the note was last changed
     */
    EDITED("noteMod"),

    /**
     * Cards -> Interval
     * Notes -> Interval
     */
    INTERVAL("cardIvl"),
    LAPSES("cardLapses"),

    /**
     * The name of the note type `Basic (and reversed card)`
     */
    NOTE_TYPE("note"),
    REVIEWS("cardReps"),

    /**
     * The inherent complexity associated with a particular memory.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_DIFFICULTY("difficulty"),

    /**
     * The probability of recalling a specific memory at a given moment.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_RETRIEVABILITY("retrievability"),

    /**
     * The time required for the probability of recall for a particular memory to decline from
     * 100% to 90%.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_STABILITY("stability"),

    /**
     * The position of the card, independent of any resets by the user.
     */
    ORIGINAL_POSITION("originalPosition"),
    ;

    companion object {
        fun fromColumnKey(key: String): CardBrowserColumn =
            entries.firstOrNull { it.ankiColumnKey == key }
                ?: throw IllegalArgumentException("Invalid key: $key")
    }
}

fun List<BrowserColumns.Column>.find(column: CardBrowserColumn): BrowserColumns.Column =
    this.firstOrNull { it.key == column.ankiColumnKey }
        ?: throw IllegalArgumentException("Invalid column: ${column.ankiColumnKey}")

/**
 * The column name: "Card Type"
 */
fun BrowserColumns.Column.getLabel(cardsOrNotes: CardsOrNotes): String = if (cardsOrNotes == CARDS) cardsModeLabel else notesModeLabel

/**
 * An optional tooltip for a column.
 *
 * This can be lengthy:
 *
 * ```
 * // Card Modified
 * "The last time changes were made to a card, including reviews, flags and deck changes"
 * ```
 *
 * https://github.com/ankitects/anki/blob/6247c92dcce0204f0e666b9e9e5355d2a15649d6/rslib/src/browser_table.rs#L192-L211
 */
@Suppress("unused")
fun BrowserColumns.Column.getTooltip(cardsOrNotes: CardsOrNotes): String? =
    (
        if (cardsOrNotes ==
            CARDS
        ) {
            cardsModeTooltip
        } else {
            notesModeTooltip
        }
    ).ifEmpty { null }
