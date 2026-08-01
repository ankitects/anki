/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

import com.ichi2.anki.Flag
import com.ichi2.anki.R
import com.ichi2.anki.browser.search.CardState
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.NoteTypeId
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.PrefsRepository
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.Json
import timber.log.Timber

private typealias Tag = String

/**
 * The user's past searches in the Card Browser.
 *
 * Displayed in most recently used order.
 */
class SearchHistory(
    private val prefs: PrefsRepository = Prefs,
    private val maxEntries: Int = MAX_ENTRIES,
) {
    /**
     * The user's past searches in the Card Browser.
     * Displayed in most recently used order.
     */
    var entries: List<SearchHistoryEntry>
        get() {
            val jsonString = prefs.getString(R.string.pref_browser_search_history, "[]") ?: "[]"
            return runCatching {
                Json.decodeFromString<List<SearchHistoryEntry>>(jsonString)
            }.getOrElse { emptyList() }
        }
        private set(value) {
            Timber.i("updating history entries: %d values", value.size)
            val json = Json.encodeToString(value)
            prefs.putString(R.string.pref_browser_search_history, json)
        }

    /**
     * Adds the provided entry to the head of the list. Returns the updated list.
     *
     * If the entry already exists, it will be moved to the head.
     */
    fun addRecent(entry: SearchHistoryEntry): List<SearchHistoryEntry> {
        val updatedEntries = entries.toMutableList()
        if (entry.isSearchEmpty()) {
            Timber.d("skipping updating history with no search")
            return updatedEntries
        }
        updatedEntries.remove(entry)
        updatedEntries.add(0, entry)
        return updatedEntries.take(maxEntries).also {
            this.entries = it.toMutableList()
            Timber.d("updated history with '%s'", entry)
        }
    }

    /**
     * Removes [entry] from [entries]. Returning whether the element was contained in the collection.
     */
    fun removeEntry(entry: SearchHistoryEntry): Boolean {
        val newEntries = entries.toMutableList()
        Timber.d("removing entry '%s'", entry)
        return newEntries.remove(entry).also {
            this.entries = newEntries
        }
    }

    fun clear() {
        Timber.i("clearing all entries")
        this.entries = listOf()
    }

    /**
     * An entry in the history of the card browser.
     * This is user-supplied, so may contain PII.
     *
     * Contains the minimal values needed for persistent serialization:
     * Deck IDs are stored, rather than deck names. See [deckIds]
     *
     * @see SearchHistory
     */
    // !! When updating this, consider equality in addRecent
    // TODO: opt-in may no longer be needed in kotlinx-serialization 1.10.0
    @OptIn(ExperimentalSerializationApi::class)
    @Serializable
    data class SearchHistoryEntry(
        @SerialName("q")
        val query: String,
        // Use IDs so we can handle a rename.
        // Tradeoff: a query to get the deck names is needed to produce a search string or display
        // the selected deck name in the UI
        @SerialName("did")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val deckIds: List<DeckId> = emptyList(),
        @SerialName("f")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val flags: List<Flag> = emptyList(),
        @SerialName("t")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val tags: List<Tag> = emptyList(),
        @SerialName("ntid")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val noteTypes: List<NoteTypeId> = emptyList(),
        @SerialName("s")
        @EncodeDefault(EncodeDefault.Mode.NEVER)
        val cardStates: List<CardState> = emptyList(),
    ) {
        @Transient
        private val allFilters = listOf(deckIds, flags, tags, noteTypes, cardStates)

        override fun toString() = query

        /**
         * Whether there is no set search - effectively a search for the default search:
         * `deck:*`
         */
        fun isSearchEmpty() = query.isBlank() && allFilters.all { it.isEmpty() }
    }

    companion object {
        /**
         * The maximum number of search history entries to store.
         * https://github.com/ankitects/anki/blob/e9cc65569807771f548fc9c2634aabc7b2f90ed2/qt/aqt/browser/browser.py#L541
         */
        const val MAX_ENTRIES = 30
    }
}
