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

import androidx.annotation.CheckResult
import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.SearchJoiner

/**
 * Represents a search in the [com.ichi2.anki.CardBrowser].
 *
 * It is not guaranteed that this request is valid. Validation is performed in [toSearchString].
 */
data class SearchRequest(
    val query: String,
    val filters: SearchFilters = SearchFilters.EMPTY,
) {
    /**
     * Syntactic sugar for using [copy], to modify [filters]
     *
     * **Usage**
     * ```kotlin
     * searchRequest.copyFilters { it.copy(decks = emptyList()) }
     * ```
     */
    @CheckResult
    fun copyFilters(filtersTransform: (SearchFilters) -> SearchFilters): SearchRequest = this.copy(filters = filtersTransform(this.filters))

    @CheckResult
    context(col: Collection)
    fun toSearchString(): Result<SearchString> {
        val (query, filters) = this
        val nodeListResult =
            runCatching {
                buildList {
                    fun <T> List<T>.toGroupNode(transform: (T) -> SearchNode): SearchNode? {
                        if (this.isEmpty()) return null
                        val searchNodes = this.map(transform)
                        return col.groupSearches(
                            searchNodes,
                            SearchJoiner.OR,
                        )
                    }

                    // a blank search should be provided if there are no filters
                    if (filters.activeFilters.isEmpty() || query.isNotBlank()) {
                        add(searchNode { parsableText = query.trim() })
                    }

                    add(
                        filters.decks.toGroupNode { nameAndId ->
                            searchNode { deck = nameAndId.name }
                        },
                    )
                    add(filters.flags.toGroupNode { it.toSearchNode() })
                    add(filters.tags.toGroupNode { t -> searchNode { tag = t } })
                    add(
                        filters.noteTypes.toGroupNode { noteType -> searchNode { note = noteType.name } },
                    )
                    add(filters.cardStates.toGroupNode { it.toSearchNode() })
                }.filterNotNull()
            }

        return nodeListResult.mapCatching { nodeList ->
            SearchString.fromNodeList(nodeList, SearchJoiner.AND).getOrThrow()
        }
    }

    companion object
}
