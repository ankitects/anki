// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

/**
 * Returns range of adapter positions of the visible items.
 *
 * This position does not include adapter changes that were dispatched after the last layout pass.
 *
 * Returns [IntRange.EMPTY] if the [LinearLayoutManager] contains no items.
 */
val LinearLayoutManager.visibleItemPositions: IntRange
    get() {
        val first = findFirstVisibleItemPosition()
        val last = findLastVisibleItemPosition()
        if (first == RecyclerView.NO_POSITION || last == RecyclerView.NO_POSITION) {
            return IntRange.EMPTY
        }
        return first..last
    }

/**
 * Returns true if the position is currently visible.
 */
fun LinearLayoutManager.positionIsVisible(position: Int): Boolean = position in visibleItemPositions
