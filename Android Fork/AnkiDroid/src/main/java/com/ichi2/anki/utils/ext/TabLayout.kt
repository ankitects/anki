// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Rakshita Chauhan <chauhanrakshita64@gmail.com>

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
