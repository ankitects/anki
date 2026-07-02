/*
 *  Copyright (c) 2025 Rakshita Chauhan <chauhanrakshita64@gmail.com>
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

import com.google.android.material.tabs.TabLayout

/**
 * Performs the given action when a tab is selected.
 * @param action The callback to be invoked when a tab is selected.
 * @return The created [TabLayout.OnTabSelectedListener], which can be used to remove the listener if needed.
 */
inline fun TabLayout.doOnTabSelected(crossinline action: (tab: TabLayout.Tab) -> Unit) =
    object : TabLayout.OnTabSelectedListener {
        override fun onTabSelected(tab: TabLayout.Tab) = action(tab)

        override fun onTabUnselected(tab: TabLayout.Tab) {}

        override fun onTabReselected(tab: TabLayout.Tab) {}
    }.also { addOnTabSelectedListener(it) }
