// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import androidx.annotation.MainThread
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import timber.log.Timber

/**
 * Shows the dialog synchronously via [DialogFragment.showNow] if [manager] has not saved its state
 * and no fragment with [tag] is already present.
 *
 * @throws IllegalStateException if [manager] cannot commit immediately, for example while it is
 * already executing transactions.
 */
@MainThread
fun DialogFragment.showOnce(
    manager: FragmentManager,
    tag: String,
) {
    if (manager.isStateSaved) {
        Timber.w("Skipping dialog %s: fragment manager state is saved", tag)
        return
    }
    if (manager.findFragmentByTag(tag) != null) {
        Timber.w("Skipping dialog %s: a fragment with this tag already exists", tag)
        return
    }

    // Register immediately so repeated calls cannot enqueue duplicate dialogs.
    showNow(manager, tag)
}
