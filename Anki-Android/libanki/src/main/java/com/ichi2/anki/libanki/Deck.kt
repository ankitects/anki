/*
 * Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.libanki

import anki.decks.Deck.Filtered.SearchTerm.Order
import com.ichi2.anki.common.utils.ext.deepClonedInto
import com.ichi2.anki.libanki.utils.NotInPyLib
import net.ankiweb.rsdroid.Translations
import org.json.JSONObject

class Deck : JSONObject {
    /**
     * Creates a copy from [JSONObject] and use it as a string
     *
     * This function will perform deepCopy on the passed object
     *
     * If you want to create a Deck without deepCopy
     * @see Deck.from
     */
    constructor(json: JSONObject) : super() {
        json.deepClonedInto(this)
    }

    /**
     * Creates a deck object form a json string
     */
    constructor(json: String) : super(json)

    val isFiltered: Boolean
        get() = getInt("dyn") != 0

    val isNormal: Boolean
        get() = !isFiltered

    var name: String
        get() = getString("name")
        set(value) {
            put("name", value)
        }

    var collapsed: Boolean
        get() = getBoolean("collapsed")
        set(value) {
            put("collapsed", value)
        }

    var browserCollapsed: Boolean
        get() = optBoolean("browserCollapsed", false)
        set(value) {
            put("browserCollapsed", value)
        }

    /**
     * Unique identifier of the deck
     *
     * @see DeckId
     */
    var id: DeckId
        get() = getLong("id")
        set(value) {
            put("id", value)
        }

    var conf: DeckConfigId
        get() {
            val value = optLong("conf")
            return if (value > 0) value else 1
        }
        set(value) {
            put("conf", value)
        }

    /**
     * The description, shown on the deck overview and optionally the congratulations screen.
     *
     * May be HTML or Markdown, depending on [descriptionAsMarkdown].
     */
    var description: String
        get() = optString("desc", "")
        set(value) {
            put("desc", value)
        }

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
    var descriptionAsMarkdown: Boolean
        get() = optBoolean("md", false)
        set(value) {
            put("md", value)
        }
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
        Order.UNRECOGNIZED -> throw IllegalArgumentException("Can't display an unknown enum value.")
    }

@NotInPyLib
internal fun Deck.confOrNull(): DeckConfigId? =
    try {
        val value = getLong("conf")
        if (value > 0) value else null
    } catch (e: Exception) {
        null
    }
