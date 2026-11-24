// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

/**
 * A named query for the Card Browser
 *
 * Selecting a saved search quickly allows a user to either:
 * - search the given query
 * - add additional terms to the query before searching
 *
 * @see com.ichi2.anki.common.utils.ext.savedFilters
 */
data class SavedSearch(
    val name: String,
    val query: String,
) {
    fun normalize() = SavedSearch(name = this.name, query = this.query.trim())
}

fun List<SavedSearch>.toMap(): Map<String, String> = associate { it.name to it.query }
