/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

import androidx.annotation.LayoutRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction

/**
 * Removes the [android.app.Fragment] attached to a view
 *
 * @param containerViewId The layout provided to [FragmentTransaction.replace]
 *
 * @throws IllegalArgumentException If a fragment is not attached to [containerViewId]
 */
fun FragmentManager.removeFragmentFromContainer(
    @LayoutRes containerViewId: Int,
) {
    val toRemove = requireNotNull(findFragmentById(containerViewId)) { "could not find fragment" }
    beginTransaction()
        .remove(toRemove)
        .commit()
}
