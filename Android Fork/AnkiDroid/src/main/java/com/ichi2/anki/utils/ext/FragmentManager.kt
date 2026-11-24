// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.annotation.LayoutRes
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.commit

/** Removes the fragment with [tag], if present. */
fun FragmentManager.removeFragmentByTag(tag: String) {
    val fragment = findFragmentByTag(tag) ?: return
    commit { remove(fragment) }
}

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
