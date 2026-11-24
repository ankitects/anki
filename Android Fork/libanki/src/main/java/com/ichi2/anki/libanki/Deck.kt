// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki

import androidx.annotation.VisibleForTesting
import anki.decks.Deck.Filtered.SearchTerm.Order
import com.ichi2.anki.common.json.JSONObjectHolder
import com.ichi2.anki.common.json.jsonArray
import com.ichi2.anki.common.json.jsonBoolean
import com.ichi2.anki.common.json.jsonLong
import com.ichi2.anki.common.json.jsonString
import com.ichi2.anki.common.utils.ext.getLongOrNull
import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.Translations
import org.intellij.lang.annotations.Language
import org.json.JSONArray
import org.json.JSONObject

/** Wraps legacy deck JSON without copying it. Property changes update the supplied object. */
data class Deck(
    @VisibleForTesting override val jsonObject: JSONObject,
) : JSONObjectHolder {
    /**
     * Creates a deck object from a JSON string
     */
    constructor(
        @Language("JSON") json: String,
    ) : this(JSONObject(json))

    /**
     * Whether this deck is a filtered deck.
     */
    val isFiltered: Boolean
        get() = jsonObject.getInt("dyn") != 0

    /**
     * Whether this deck is a normal deck. That is, not a filtered deck.
     */
    val isNormal: Boolean
        get() = !isFiltered

    /**
     * The name of the deck. Mutable. If you want a way to persistently represents this deck, use [id] instead.
     */
    var name by jsonString("name")

    /**
     * If this deck has subdecks, whether those subdecks should be collapsed in the desktop card browser.
     * Not used in ankidroid at the moment.
     */
    var browserCollapsed by jsonBoolean("browserCollapsed", defaultValue = false)

    /**
     * If this deck has subdecks, whether those subdecks should be collapsed in the deck picker.
     */
    var collapsed by jsonBoolean("collapsed")

    /**
     * The id of the deck. Should be globally unique
     * (created as a timestamp, very small chance of collision between two different decks from different users)
     *
     * @see DeckId
     */
    var id: DeckId by jsonLong("id")

    /**
     * The id of the deck option.
     */
    var conf: DeckConfigId
        get() {
            val value = jsonObject.optLong("conf")
            return if (value > 0) value else 1
        }
        set(value) {
            jsonObject.put("conf", value)
        }

    var noteTypeId: NoteTypeId?
        get() = jsonObject.getLongOrNull("mid")
        set(value) {
            jsonObject.put("mid", value)
        }

    var resched by jsonBoolean("resched")

    /**
     * The options configuring which cards are shown in a filtered deck.
     * See https://docs.ankiweb.net/filtered-decks.html
     */
    @JvmInline
    value class Term(
        val array: JSONArray,
    ) {
        constructor(search: String, limit: Int, order: Int) : this(JSONArray(listOf(search, limit, order))) {}

        /**
         Only cards satisfying this search query are shown.
         */
        var search: String
            get() = array.getString(0)
            set(value) {
                array.put(0, value)
            }

        /**
         * At most this number of cards are shown.
         */
        var limit: Int
            get() = array.getInt(1)
            set(value) {
                array.put(1, value)
            }

        /**
         * The order in which cards are shown. See https://docs.ankiweb.net/filtered-decks.html#order.
         */
        var order: Int
            get() = array.getInt(2)
            set(value) {
                array.put(2, value)
            }

        override fun toString(): String = array.toString()
    }

    /**
     * The options deciding which cards are shown in a filtered deck.
     */
    val firstFilter: Term
        get() = Term(terms.getJSONArray(0))

    /**
     * An optional second search filter. Setting this to null removes only that filter.
     */
    var secondFilter: Term?
        get() = terms.optJSONArray(1)?.let { Term(it) }
        set(value) {
            if (value == null) {
                terms.remove(1)
            } else {
                terms.put(1, value.array)
            }
        }

    /**
     * The array of filters. Only for filtered decks.
     */
    private val terms by jsonArray("terms")

    /**
     * The description, shown on the deck overview and optionally the congratulations screen.
     *
     * May be HTML or Markdown, depending on [descriptionAsMarkdown].
     */
    var description by jsonString("desc", defaultValue = "")

    /**
     * Treats [description] as Markdown, cleaning HTML input and stripping images.
     *
     * If disabled, the description is only shown on the deck overview.
     * If enabled, it is also shown on the congratulations screen.
     *
     * Markdown will appear as text on Anki 2.1.40 and below.
     *
     * Anki names this feature 'md': Markdown description
     *
     * @see anki.backend.GeneratedBackend.renderMarkdown
     * @see anki.i18n.GeneratedTranslations.deckConfigDescriptionNewHandling
     * @see anki.i18n.GeneratedTranslations.deckConfigDescriptionNewHandlingHint
     */
    var descriptionAsMarkdown by jsonBoolean("md", defaultValue = false)

    override fun toString(): String = jsonObject.toString()
}

/**
 * Converts a Sort Order for a filtered deck to a display string
 *
 * `Order.OLDEST_REVIEWED_FIRST` -> "Oldest seen first"
 *
 * @throws IllegalArgumentException if [Order.UNRECOGNIZED] is provided
 */
fun Order.toDisplayString(translations: Translations) =
    when (this) {
        Order.OLDEST_REVIEWED_FIRST -> translations.decksOldestSeenFirst()
        Order.RANDOM -> translations.decksRandom()
        Order.INTERVALS_ASCENDING -> translations.decksIncreasingIntervals()
        Order.INTERVALS_DESCENDING -> translations.decksDecreasingIntervals()
        Order.LAPSES -> translations.decksMostLapses()
        Order.ADDED -> translations.decksOrderAdded()
        Order.DUE -> translations.decksOrderDue()
        Order.REVERSE_ADDED -> translations.decksLatestAddedFirst()
        Order.RETRIEVABILITY_ASCENDING -> translations.deckConfigSortOrderRetrievabilityAscending()
        Order.RETRIEVABILITY_DESCENDING -> translations.deckConfigSortOrderRetrievabilityDescending()
        Order.RELATIVE_OVERDUENESS -> translations.decksRelativeOverdueness()
        Order.UNRECOGNIZED -> throw IllegalArgumentException("Can't display an unknown enum value.")
    }

@NotInPyLib
internal fun Deck.confOrNull(): DeckConfigId? =
    try {
        val value = jsonObject.getLong("conf")
        if (value > 0) value else null
    } catch (e: Exception) {
        null
    }
