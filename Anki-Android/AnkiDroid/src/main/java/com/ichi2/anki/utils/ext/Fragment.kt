/*
 *  Copyright (c) 2024 Brayan Oliveira <brayandso.dev@gmail.com>
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

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import android.view.Window
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.utils.showDialogFragmentImpl

fun Fragment.sharedPrefs(): SharedPreferences = requireContext().sharedPrefs()

val Fragment.packageManager: PackageManager
    get() = requireContext().packageManager

/**
 * Method to show dialog fragment including adding it to back stack
 *
 * @see showDialogFragmentImpl
 */
fun Fragment.showDialogFragment(newFragment: DialogFragment) = requireActivity().showDialogFragment(newFragment)

/** @see FragmentActivity.getWindow */
val Fragment.window: Window
    get() = requireActivity().window

/** Breakpoint for the "Compact" width size class */
private const val COMPACT_WIDTH_DP_MAX = 600

/**
 * Determines if the current window is considered "small" based on the "Compact" Window Size Class.
 * This checks if the **current window width** (via screenWidthDp) is less than 600dp.
 *
 * @return `true` if the current window width is < 600dp, `false` otherwise.
 *
 * @see [Window Size Classes](https://developer.android.com/develop/ui/compose/layouts/adaptive/use-window-size-classes)
 */
val Fragment.isCompactWidth: Boolean
    get() {
        val currentWidthInDp = requireContext().resources.configuration.screenWidthDp
        return currentWidthInDp < COMPACT_WIDTH_DP_MAX
    }

/**
 * Registers a lifecycle-aware [ViewTreeObserver.OnWindowFocusChangeListener].
 * The listener is automatically removed when the fragment's view is destroyed.
 *
 * Must be called after the view is created (e.g. in [Fragment.onViewCreated]).
 */
fun Fragment.onWindowFocusChanged(action: (hasFocus: Boolean) -> Unit) {
    val listener = OnWindowFocusChangeListener(action)
    requireView().viewTreeObserver.addOnWindowFocusChangeListener(listener)
    viewLifecycleOwner.lifecycle.addObserver(
        object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                view
                    ?.viewTreeObserver
                    ?.takeIf { it.isAlive }
                    ?.removeOnWindowFocusChangeListener(listener)
            }
        },
    )
}
