// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.utils.ext.DIALOG_FRAGMENT_TAG

/**
 * Global method to show dialog fragment including adding it to back stack
 * If you need to show a dialog from an async task, use [AnkiActivity.showAsyncDialogFragment]
 *
 * @param manager The [FragmentManager] of the activity/fragment
 * @param newFragment the [DialogFragment] you want to show
 */
fun showDialogFragmentImpl(
    manager: FragmentManager,
    newFragment: DialogFragment,
) {
    // A pending dismissal can destroy the previous dialog before this transaction removes it.
    manager.executePendingTransactions()

    // DialogFragment.show() will take care of adding the fragment
    // in a transaction. We also want to remove any currently showing
    // dialog, so make our own transaction and take care of that here.
    val ft = manager.beginTransaction()
    val prev = manager.findFragmentByTag(DIALOG_FRAGMENT_TAG)
    if (prev != null) {
        ft.remove(prev)
    }
    // save transaction to the back stack
    ft.addToBackStack(DIALOG_FRAGMENT_TAG)
    newFragment.show(ft, DIALOG_FRAGMENT_TAG)
    manager.executePendingTransactions()
}

/**
 * Convenience function for calling [showDialogFragmentImpl] with a certain [FragmentManager],
 * as opposed to [com.ichi2.anki.utils.ext.showDialogFragment], which always calls it with the activity-level
 * support fragment manager. This is useful when you want a dialog to be scoped to a fragment's lifecycle.
 */
fun FragmentManager.showDialogFragment(newFragment: DialogFragment) = showDialogFragmentImpl(this, newFragment)
