// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.common.utils.ext.savedFilters
import timber.log.Timber

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
