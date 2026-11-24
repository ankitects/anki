// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.preferences

import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceFragmentCompat

/**
 * By implementing this interface, a preference can specify which dialog fragment is opened on click.
 * The library does this in a slightly roundabout way, requiring that
 * a third party is aware of all the combinations of preferences and their dialogs;
 * see [PreferenceFragmentCompat.onDisplayPreferenceDialog].
 */
interface DialogFragmentProvider {
    /**
     * @return A DialogFragment to show or `null` to use the parent fragment
     *   The dialog must have a zero-parameter constructor.
     *   Any arguments set via [Fragment.setArguments] may get overridden.
     */
    fun makeDialogFragment(): DialogFragment?
}
