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

import android.view.View
import androidx.annotation.IdRes
import androidx.recyclerview.widget.RecyclerView

/** @see View.findViewById */
fun <T : View> RecyclerView.ViewHolder.findViewById(
    @IdRes id: Int,
) = itemView.findViewById<T>(id)

/**
 * Adds a listener invoked whenever the [RecyclerView] has completed scrolling.
 *
 * - `dx` - The amount of horizontal scroll.
 * - `dy` - The amount of vertical scroll.
 *
 * @see RecyclerView.OnScrollListener.onScrolled
 */
inline fun RecyclerView.doOnScrolled(crossinline action: (dx: Int, dy: Int) -> Unit) {
    addOnScrollListener(
        object : RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: RecyclerView,
                dx: Int,
                dy: Int,
            ) = action(dx, dy)
        },
    )
}
