/*
 *  Copyright (c) 2022 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.utils

import android.widget.Filter

/** Implementation of [Filter] which is strongly typed */
abstract class TypedFilter<T>(
    private val getCurrentItems: (() -> List<T>),
) : Filter() {
    constructor(items: List<T>) : this({ items })

    var lastConstraint: CharSequence? = null

    fun refresh() {
        filter(lastConstraint)
    }

    override fun performFiltering(constraint: CharSequence?): FilterResults {
        val itemsBeforeFiltering = getCurrentItems()

        if (constraint.isNullOrBlank()) {
            return FilterResults().also {
                it.values = itemsBeforeFiltering
                it.count = itemsBeforeFiltering.size
            }
        }

        val items = filterResults(constraint, itemsBeforeFiltering)

        return FilterResults().also {
            it.values = items
            it.count = items.size
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun publishResults(
        constraint: CharSequence?,
        results: FilterResults?,
    ) {
        // this is only ever called from performFiltering so we can guarantee the value can be cast to List<T>
        if (results?.values != null) {
            val list = results.values as List<T>
            publishResults(constraint, list)
        }
    }

    /**
     * Filters [items] based on the [constraint]. [constraint] is non-empty
     *
     * @see Filter.performFiltering
     */
    abstract fun filterResults(
        constraint: CharSequence,
        items: List<T>,
    ): List<T>

    /** @see android.widget.Filter.publishResults */
    abstract fun publishResults(
        constraint: CharSequence?,
        results: List<T>,
    )
}
