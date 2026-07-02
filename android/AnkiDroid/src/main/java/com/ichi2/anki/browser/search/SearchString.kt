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
import com.ichi2.anki.browser.search.SearchString.Companion.fromNodeList
import com.ichi2.anki.browser.search.SearchString.Companion.fromUserInput
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.SearchJoiner
import com.ichi2.anki.libanki.SortOrder
import com.ichi2.anki.libanki.exception.InvalidSearchException
import net.ankiweb.rsdroid.BackendException.BackendSearchException

/**
 * A valid string for [findCards][Collection.findCards]/[findNotes][Collection.findNotes]
 *
 * Constructed using [fromUserInput] or [fromNodeList]
 */
@JvmInline
value class SearchString private constructor(
    val value: String,
) {
    val length get() = value.length

    override fun toString(): String = value

    companion object {
        val EMPTY: SearchString = SearchString("")

        /**
         * Returns a valid search string or:
         * - A wrapped [InvalidSearchException] if the search was invalid (e.g. `and`)
         *
         * @see Collection.buildSearchString
         */
        @CheckResult
        context(col: Collection)
        fun fromUserInput(query: String): Result<SearchString> =
            runCatching { col.buildSearchString(listOf(query)) }
                .map { SearchString(it) }
                .mapFailure { e ->
                    when (e) {
                        is BackendSearchException -> InvalidSearchException(e)
                        else -> e
                    }
                }

        /**
         * Returns a valid search string or:
         * - A wrapped [IllegalArgumentException] if [searchNodes] is empty
         * - A wrapped [InvalidSearchException] if the search was invalid
         *
         * @see Collection.buildSearchString
         */
        @CheckResult
        context(col: Collection)
        fun fromNodeList(
            searchNodes: List<SearchNode>,
            joiner: SearchJoiner = SearchJoiner.AND,
        ): Result<SearchString> =
            runCatching { col.buildSearchString(searchNodes, joiner) }
                .map { SearchString(it) }
                .mapFailure { e ->
                    when (e) {
                        is BackendSearchException -> InvalidSearchException(e)
                        else -> e
                    }
                }
    }
}

/**
 * @see Collection.findCards
 */
fun Collection.findCards(
    search: SearchString,
    order: SortOrder = SortOrder.NoOrdering,
) = this.findCards(search.value, order)

/**
 * @see Collection.findNotes
 */
fun Collection.findNotes(
    search: SearchString,
    order: SortOrder = SortOrder.NoOrdering,
) = this.findNotes(search.value, order)

/**
 * Returns the encapsulated result of the given [transform] function applied to the encapsulated exception
 * if this instance represents [failure][Result.isFailure] or the
 * original encapsulated value if it is [success][Result.isSuccess].
 *
 * This function throws if [transform] throws. See [recoverCatching] for an alternative that encapsulates exceptions.
 */
private inline fun <T> Result<T>.mapFailure(transform: (exception: Throwable) -> Throwable): Result<T> =
    when (val exception = exceptionOrNull()) {
        null -> this
        else -> Result.failure(transform(exception))
    }
