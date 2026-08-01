/*
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

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
