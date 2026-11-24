// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import androidx.annotation.StringRes
import anki.search.BrowserColumns
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CommonString
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.model.CardsOrNotes.CARDS

/**
 * The data type associated with a browser column.
 *
 * Used to explain what 'ascending/descending' means in terms of sorting the column.
 */
// This is useful from a UX perspective as arrows are ambiguous when it comes to sorting.
// An additional textual explanation, or 1↓ on a vertical list is clearer.
enum class ColumnType {
    /**
     * Sorted alphabetically
     * Visually: A↓ or Z↓
     */
    TEXT,

    /**
     * A simple numeric value.
     * Visually: `1↓` or `9↓`
     */
    NUMERIC,

    /**
     * A date/time
     * Visually: `↓` or `↑`
     */
    DATE,

    /**
     * Used when the type cannot be determined, or varies by context.
     *
     * For example, [CardBrowserColumn.DUE] may display a position number or a date
     * depending on the card's state.
     *
     * Visually: `↓` or `↑`
     */
    UNSPECIFIED,
}

/**
 * A column available in the [browser][CardBrowser]
 *
 * @see [anki.search.BrowserRow] for data associated with a column
 *
 * @param ankiColumnKey The key used in [Backend.setActiveBrowserColumns]
 * @param cardsType The data type displayed by the column in cards mode
 * @param notesType The data type displayed by the column in notes mode. Defaults to [cardsType].
 */
enum class CardBrowserColumn(
    val ankiColumnKey: String,
    private val cardsType: ColumnType,
    private val notesType: ColumnType = cardsType,
) {
    /** Rendered front side of the first card of the note */
    QUESTION("question", ColumnType.TEXT),

    /** Rendered back side of the first card of the note */
    ANSWER("answer", ColumnType.TEXT),

    /** The value of the field marked as "Sort by this field in the Browser" */
    SFLD("noteFld", ColumnType.TEXT),

    /**
     * Cards -> The deck which contains the card
     * Notes -> Either the deck containing the card, or `(n)`, where n is the number of
     * distinct decks
     */
    DECK("deck", ColumnType.TEXT),

    /** A list of tags for the note */
    TAGS("noteTags", ColumnType.TEXT),

    /**
     * Cards -> Card Type (template name)
     * Notes -> Cards (card count)
     */
    CARD("template", cardsType = ColumnType.TEXT, notesType = ColumnType.NUMERIC),
    DUE("cardDue", ColumnType.UNSPECIFIED),

    /**
     * Cards -> Ease
     * Notes -> Average Ease
     */
    EASE("cardEase", ColumnType.NUMERIC),

    /**
     * Cards -> Timestamp the card was modified
     * Notes -> Most recent timestamp a card of the note was modified
     */
    CHANGED("cardMod", ColumnType.DATE),

    /**
     * Timestamp the note was created
     */
    CREATED("noteCrt", ColumnType.DATE),

    /**
     * Timestamp the note was last changed
     */
    EDITED("noteMod", ColumnType.DATE),

    /**
     * Cards -> Interval
     * Notes -> Interval
     */
    INTERVAL("cardIvl", ColumnType.NUMERIC),
    LAPSES("cardLapses", ColumnType.NUMERIC),

    /**
     * The name of the note type `Basic (and reversed card)`
     */
    NOTE_TYPE("note", ColumnType.TEXT),
    REVIEWS("cardReps", ColumnType.NUMERIC),

    /**
     * The inherent complexity associated with a particular memory.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_DIFFICULTY("difficulty", ColumnType.NUMERIC),

    /**
     * The probability of recalling a specific memory at a given moment.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_RETRIEVABILITY("retrievability", ColumnType.NUMERIC),

    /**
     * The time required for the probability of recall for a particular memory to decline from
     * 100% to 90%.
     * Used in FSRS, blank if using SM-2
     */
    FSRS_STABILITY("stability", ColumnType.NUMERIC),

    /**
     * The position of the card, independent of any resets by the user.
     */
    ORIGINAL_POSITION("originalPosition", ColumnType.NUMERIC),
    ;

    /** The data type displayed by the column for the given [cardsOrNotes] mode. */
    fun type(cardsOrNotes: CardsOrNotes): ColumnType = if (cardsOrNotes == CARDS) cardsType else notesType

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

/**
 * An explanation of how sorting occurs: (e.g. "High to low"; "Newest first")
 */
@StringRes
fun ColumnType.humanReadableExplanation(descending: Boolean): Int? =
    if (descending) {
        when (this) {
            ColumnType.TEXT -> CommonString.card_browser_order_subtitle_text_descending
            ColumnType.NUMERIC -> CommonString.card_browser_order_subtitle_numeric_descending
            ColumnType.DATE -> CommonString.card_browser_order_subtitle_date_descending
            ColumnType.UNSPECIFIED -> null
        }
    } else {
        when (this) {
            ColumnType.TEXT -> CommonString.card_browser_order_subtitle_text_ascending
            ColumnType.NUMERIC -> CommonString.card_browser_order_subtitle_numeric_ascending
            ColumnType.DATE -> CommonString.card_browser_order_subtitle_date_ascending
            ColumnType.UNSPECIFIED -> null
        }
    }
