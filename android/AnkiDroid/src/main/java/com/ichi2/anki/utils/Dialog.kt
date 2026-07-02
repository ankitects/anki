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
