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

package com.ichi2.anki.browser.search

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.libanki.Config
import timber.log.Timber

/**
 * A named query for the Card Browser
 *
 * Selecting a saved search quickly allows a user to either:
 * - search the given query
 * - add additional terms to the query before searching
 *
 * @see SavedSearches
 */
data class SavedSearch(
    val name: String,
    val query: String,
) {
    fun normalize() = SavedSearch(name = this.name, query = this.query.trim())
}

/**
 * Manages saved searches (named search queries in the Card Browser)
 *
 * Named 'Saved Searches' in Anki Desktop
 *
 * Searches are shared between all Anki clients in the collection, are unordered and are
 * case-sensitive: 'A' and 'a' are different searches with the ordering: `["A", "Z", "a"]`
 *
 * @see SavedSearch
 * @see savedFilters
 */
object SavedSearches {
    /**
     * Returns the list of [saved searches][SavedSearch] stored in the Anki collection config.
     */
    suspend fun loadFromConfig(): List<SavedSearch> = withCol { config.savedFilters }

    /**
     * Updates the list of [saved searches][SavedSearch] stored in the Anki collection config.
     *
     * Ordering is NOT preserved
     */
    suspend fun saveToConfig(values: List<SavedSearch>) = withCol { config.savedFilters = values }

    /**
     * Returns a saved search with a given name (case sensitive)
     */
    suspend fun byName(name: String) = loadFromConfig().find { it.name == name }

    /**
     * Adds a saved search to the Anki collection config
     *
     * @return a pair: `false` if a search with the given name already exists,
     * `true` if the search was added.
     *
     * The second element of the pair is the updated list of saved searches.
     */
    suspend fun add(savedSearch: SavedSearch): Pair<Boolean, List<SavedSearch>> {
        Timber.i("saving user search")
        val values = loadFromConfig()
        if (values.any { it.name == savedSearch.name }) return false to values
        val updatedValues = values + savedSearch.normalize()
        saveToConfig(updatedValues)
        return true to loadFromConfig()
    }

    /**
     * Removes a saved search from the Anki collection by name
     *
     * @return a pair: `true` if the searches were updated, `false` if the name was not found
     *
     * The second element of the pair is the updated list of saved searches.
     */
    suspend fun removeByName(searchName: String): Pair<Boolean, List<SavedSearch>> {
        Timber.i("removing saved search")
        val originalValues = loadFromConfig()
        val updatedValues = originalValues.filter { it.name != searchName }
        // early return if no changes occurred
        if (updatedValues.size == originalValues.size) return false to originalValues
        saveToConfig(updatedValues)
        return true to updatedValues
    }

    /** Removes all saved searches from the Anki collection */
    suspend fun clear() = saveToConfig(emptyList())
}

/**
 * The list of saved searches in the Anki Collection
 *
 * Ordering is NOT preserved in Anki Desktop. Searches are ordered based on the name and are
 * case-sensitive
 */
var Config.savedFilters: List<SavedSearch>
    get() =
        get<Map<String, String>>("savedFilters")
            .orEmpty()
            .map { (key, value) -> SavedSearch(name = key, query = value) }
    set(value) {
        set("savedFilters", value.toMap())
    }

fun List<SavedSearch>.toMap(): Map<String, String> = associate { it.name to it.query }
