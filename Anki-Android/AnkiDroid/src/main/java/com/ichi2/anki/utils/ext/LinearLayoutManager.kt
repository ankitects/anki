/*
 *  Copyright (c) 2025 David Allison <davidallisongithub@gmail.com>
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
