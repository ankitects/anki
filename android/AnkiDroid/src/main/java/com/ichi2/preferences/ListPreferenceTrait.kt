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
 *
 *  This file incorporates code under the following license
 *
 *     Copyright 2018 The Android Open Source Project
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 * Source: androidx.preference.ListPreferenceDialogFragmentCompat
 *
 * Changes:
 * * Convert to Kotlin
 * * Remove Hungarian Notation
 * * Rely on `ListPreferenceTrait` rather than `ListPreference`
 * * `Bundle().apply` usage
 * * `if (preference.callChangeListener(value)) {` - depend on `preference` and `listPreference`
 */

package com.ichi2.preferences

import android.content.DialogInterface
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreferenceDialogFragmentCompat
import androidx.preference.Preference
import androidx.preference.PreferenceDialogFragmentCompat
import androidx.preference.R

// This exists as we want a dialog to show either a List or an EditText, and both the framework
// classes require inheritance

/**
 * A [Preference] may inherit from [ListPreferenceTrait] if it wishes to
 * optionally display a List-based dialog.
 * To do so, return a [ListPreferenceDialogFragment] via [DialogFragmentProvider.makeDialogFragment]
 */
interface ListPreferenceTrait : DialogFragmentProvider {
    var listEntries: List<Entry>
    val entryKeys get() = listEntries.map { it.key }
    val entryValues get() = listEntries.map { it.value }

    fun indexOf(value: String) = listEntries.indexOfFirst { it.value == value }

    var listValue: String

    data class Entry(
        val key: String,
        val value: String,
    )

    companion object {
        /** to be passed into defStyleAttr in the [Preference] constructor */
        val STYLE_ATTR = R.attr.dialogPreferenceStyle
    }
}

/**
 * A fragment which may be launched from a [Preference] implementing a [ListPreferenceTrait]
 *
 * Adapted from: [ListPreferenceDialogFragmentCompat]
 * @see ListPreferenceDialogFragmentCompat
 */
class ListPreferenceDialogFragment : PreferenceDialogFragmentCompat() {
    // synthetic access
    private var clickedDialogEntryIndex = 0
    private lateinit var entries: Array<CharSequence>
    private lateinit var entryValues: Array<CharSequence>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            val preference = listPreference
            clickedDialogEntryIndex = preference.indexOf(preference.listValue)
            entries = preference.entryKeys.toTypedArray()
            entryValues = preference.entryValues.toTypedArray()
        } else {
            clickedDialogEntryIndex = savedInstanceState.getInt(SAVE_STATE_INDEX, 0)
            entries = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRIES)!!
            entryValues = savedInstanceState.getCharSequenceArray(SAVE_STATE_ENTRY_VALUES)!!
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(SAVE_STATE_INDEX, clickedDialogEntryIndex)
        outState.putCharSequenceArray(SAVE_STATE_ENTRIES, entries)
        outState.putCharSequenceArray(SAVE_STATE_ENTRY_VALUES, entryValues)
    }

    private val listPreference get() = preference as ListPreferenceTrait

    override fun onPrepareDialogBuilder(builder: AlertDialog.Builder) {
        super.onPrepareDialogBuilder(builder)
        builder.setSingleChoiceItems(
            entries,
            clickedDialogEntryIndex,
        ) { dialog, which ->
            clickedDialogEntryIndex = which

            // Clicking on an item simulates the positive button click, and dismisses
            // the dialog.
            this.onClick(
                dialog,
                DialogInterface.BUTTON_POSITIVE,
            )
            dialog.dismiss()
        }

        // The typical interaction for list-based dialogs is to have click-on-an-item dismiss the
        // dialog instead of the user having to press 'Ok'.
        builder.setPositiveButton(null, null)
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        if (positiveResult && clickedDialogEntryIndex >= 0) {
            val value = entryValues[clickedDialogEntryIndex].toString()
            if (preference.callChangeListener(value)) {
                listPreference.listValue = value
            }
        }
    }

    companion object {
        private const val SAVE_STATE_INDEX = "ListPreferenceDialogFragment.index"
        private const val SAVE_STATE_ENTRIES = "ListPreferenceDialogFragment.entries"
        private const val SAVE_STATE_ENTRY_VALUES = "ListPreferenceDialogFragment.entryValues"

        fun newInstance(key: String?): ListPreferenceDialogFragment =
            ListPreferenceDialogFragment().apply {
                arguments = Bundle().apply { putString(ARG_KEY, key) }
            }
    }
}
