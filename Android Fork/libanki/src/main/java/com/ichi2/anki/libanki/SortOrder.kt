// SPDX-License-Identifier: GPL-3.0-or-later

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
