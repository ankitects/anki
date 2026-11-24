// SPDX-License-Identifier: GPL-3.0-or-later

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
