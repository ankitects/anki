// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import androidx.annotation.CheckResult
import anki.search.SearchNode
import anki.search.searchNode
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.DeckNameId
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

                    add(filters.decks.toGroupNode { it.toSearchNode() })
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

/**
 * Uses the deck's current name, as it may have been renamed after it was selected.
 *
 * The backend escapes wildcards in deck names, except for a deck named exactly `_*`,
 * which is left as a wildcard matching every deck
 */
context(col: Collection)
private fun DeckNameId.toSearchNode(): SearchNode {
    val currentName = col.decks.nameIfExists(id) ?: name
    return if (currentName == "_*") {
        searchNode { parsableText = """deck:\_\*""" }
    } else {
        searchNode { deck = currentName }
    }
}
