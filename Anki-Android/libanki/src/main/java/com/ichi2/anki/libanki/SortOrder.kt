/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.libanki

import anki.search.BrowserColumns.Column
import anki.search.BrowserColumns.Sorting

/**
 * Options for sorting in [Collection.findNotes] or [Collection.findCards]
 *
 * Pylib implements this using a union, and a 'reverse' variable which only applies
 *  if using BrowserColumns.Column:
 *
 * ```python
 *         order: bool | str | BrowserColumns.Column = False,
 *         reverse: bool = False,
 * ```
 *
 * https://github.com/ankitects/anki/blob/6247c92dcce0204f0e666b9e9e5355d2a15649d6/pylib/anki/collection.py#L643-L663
 */
sealed class SortOrder {
    /**
     * Search results are returned with no ordering.
     *
     * **Python:**
     * ```python
     * order=False
     * ```
     */
    data object NoOrdering : SortOrder()

    /**
     * Use the sort order stored in the collection config
     *
     * `sortType` and `sortBackwards`
     *
     * **Python:**
     * ```python
     * order=True
     * ```
     */
    data object UseCollectionOrdering : SortOrder()

    /**
     * Text which is added after 'order by' in the sql statement.
     *
     * You must add ' asc' or ' desc' to the order, as Anki will replace asc with
     * desc and vice versa when reverse is set in the collection config, e.g.
     * `c.ivl asc, c.due desc`.
     * */
    data class AfterSqlOrderBy(
        val customOrdering: String,
    ) : SortOrder()

    /**
     * Sort using a column, if it supports sorting.
     *
     * All available columns are available through [Collection.allBrowserColumns]
     * and support sorting cards unless [Column.getSortingCards]/[Column.getSortingNotes]
     * is set to [Sorting.SORTING_NONE]
     */
    class BuiltinColumnSortKind(
        val column: Column,
        val reverse: Boolean,
    ) : SortOrder() {
        override fun toString() = "BuiltinColumnSortKind(column=${column.key}, reverse=$reverse)"
    }
}
