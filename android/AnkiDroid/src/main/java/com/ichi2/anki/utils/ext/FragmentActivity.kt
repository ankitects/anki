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

import android.os.Bundle
import android.view.View
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.utils.showDialogFragmentImpl

/*
 ************************************************************************************************
 ****************************************** Dialogs *********************************************
 ************************************************************************************************
 */

const val DIALOG_FRAGMENT_TAG = "dialog"

/**
 * Global method to show dialog fragment including adding it to back stack
 * If you need to show a dialog from an async task, use [AnkiActivity.showAsyncDialogFragment]
 *
 * @param newFragment the [DialogFragment] you want to show
 */
fun FragmentActivity.showDialogFragment(newFragment: DialogFragment) {
    runOnUiThread {
        showDialogFragmentImpl(this.supportFragmentManager, newFragment)
    }
}

/** Dismiss whatever dialog is showing */
fun FragmentActivity.dismissAllDialogFragments() {
    // trying to pop fragment manager back state crashes if state already saved
    if (!supportFragmentManager.isStateSaved) {
        supportFragmentManager.popBackStack(
            DIALOG_FRAGMENT_TAG,
            FragmentManager.POP_BACK_STACK_INCLUSIVE,
        )
    }
}

/**
 * Executes [block] after all fragments have executed `onViewCreated`
 */
fun FragmentActivity.onAllFragmentsLoaded(block: () -> Unit) {
    supportFragmentManager.registerFragmentLifecycleCallbacks(
        object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentViewCreated(
                fm: FragmentManager,
                f: Fragment,
                v: View,
                savedInstanceState: Bundle?,
            ) {
                super.onFragmentViewCreated(fm, f, v, savedInstanceState)
                if (supportFragmentManager.fragments.all { it.view != null }) {
                    try {
                        block()
                    } finally {
                        supportFragmentManager.unregisterFragmentLifecycleCallbacks(this)
                    }
                }
            }
        },
        true,
    )
}
