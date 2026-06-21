// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.destinations.DeckOptionsEntry
import com.ichi2.anki.databinding.DialogDeckOptionsSelectionBinding
import com.ichi2.anki.ui.windows.reviewer.ReviewerFragment
import com.ichi2.anki.ui.windows.reviewer.ReviewerViewModel
import com.ichi2.anki.utils.ext.usingStyledAttributes
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.title
import timber.log.Timber

/**
 * Shows a list of decks from which the user can select one to display its deck options.
 * @see ReviewerFragment
 * @see ReviewerViewModel.emitDeckOptionsDestination
 */
@NeedsTest("verify null deck names => null entry")
fun Context.showDeckOptionsSelectionDialog(
    options: List<DeckOptionsEntry>,
    onDeckSelected: (DeckOptionsEntry) -> Unit,
) {
    Timber.i("Showing deck options selection dialog")
    val binding = DialogDeckOptionsSelectionBinding.inflate(LayoutInflater.from(this))
    val normalDeckNameColor: Int =
        usingStyledAttributes(null, intArrayOf(android.R.attr.textColor)) {
            getColor(0, 0)
        }
    val dynamicDeckNameColor: Int =
        usingStyledAttributes(null, intArrayOf(R.attr.dynDeckColor)) {
            getColor(0, 0)
        }
    val dialog =
        MaterialAlertDialogBuilder(this).create {
            title(text = TR.deckConfigWhichDeck())
            customView(binding.root)
        }
    binding.deckOptionsList.adapter =
        object : ArrayAdapter<String>(
            this,
            R.layout.item_deck_option_selection,
            options.map { it.name.toString() },
        ) {
            override fun getView(
                position: Int,
                convertView: View?,
                parent: ViewGroup,
            ): View {
                val rowView = super.getView(position, convertView, parent) as TextView
                rowView.setTextColor(if (options[position].isFiltered) dynamicDeckNameColor else normalDeckNameColor)
                rowView.setOnClickListener {
                    dialog.dismiss()
                    onDeckSelected(options[position])
                }
                return rowView
            }
        }
    dialog.show()
}
