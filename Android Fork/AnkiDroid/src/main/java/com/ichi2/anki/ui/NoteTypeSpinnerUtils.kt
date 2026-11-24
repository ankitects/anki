// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.content.Context
import android.widget.ArrayAdapter
import android.widget.Spinner
import com.ichi2.anki.R
import com.ichi2.anki.common.json.NamedJSONComparator
import com.ichi2.anki.libanki.Collection

fun setupNoteTypeSpinner(
    context: Context,
    noteTypeSpinner: Spinner,
    col: Collection,
): List<Long> {
    val sortedModels = col.notetypes.all().sortedWith(NamedJSONComparator.INSTANCE)
    val modelNames = sortedModels.map { it.name }

    noteTypeSpinner.adapter =
        ArrayAdapter(
            context,
            android.R.layout.simple_spinner_dropdown_item,
            modelNames,
        ).apply {
            // The resource passed to the constructor is normally used for both the spinner view
            // and the dropdown list. This keeps the former and overrides the latter.
            setDropDownViewResource(R.layout.item_spinner_dropdown_with_radio)
        }

    return sortedModels.map { it.id }
}
