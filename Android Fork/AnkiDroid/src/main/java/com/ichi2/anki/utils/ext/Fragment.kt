// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.view.ViewTreeObserver
import android.view.ViewTreeObserver.OnWindowFocusChangeListener
import android.view.Window
import androidx.appcompat.app.AppCompatActivity
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

/**
 * Sets the title and subtitle of the host activity's support action bar.
 * A `null` [subtitle] removes it.
 */
fun Fragment.setSupportActionBarText(
    title: String,
    subtitle: String? = null,
) {
    val actionBar = (requireActivity() as AppCompatActivity).supportActionBar ?: return
    actionBar.title = title
    actionBar.subtitle = subtitle
}

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
