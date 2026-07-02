/*
 * Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>
 *
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
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.anki.utils.ext.requireLong

class DeckPickerConfirmDeleteDeckDialog : AnalyticsDialogFragment() {
    private val deckId get() = requireArguments().requireLong("deckId")
    private val deckName get() = requireArguments().getString("deckName")
    private val totalCards get() = requireArguments().getInt("totalCards")
    private val isFilteredDeck get() = requireArguments().getBoolean("isFilteredDeck")

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val message =
            if (isFilteredDeck) {
                resources.getString(R.string.delete_cram_deck_message, "<b>$deckName</b>")
            } else {
                resources.getQuantityString(
                    R.plurals.delete_deck_message,
                    totalCards,
                    "<b>$deckName</b>",
                    totalCards,
                )
            }
        super.onCreate(savedInstanceState)
        return AlertDialog
            .Builder(requireActivity())
            .setTitle(R.string.delete_deck_title)
            .setMessage(
                HtmlCompat.fromHtml(
                    message,
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                ),
            ).setIcon(R.drawable.ic_warning)
            .setPositiveButton(R.string.dialog_positive_delete) { _, _ ->
                (activity as DeckPicker).deleteDeck(deckId)
                activity?.dismissAllDialogFragments()
            }.setNegativeButton(R.string.dialog_cancel) { _, _ ->
                activity?.dismissAllDialogFragments()
            }.create()
    }

    companion object {
        fun newInstance(
            deckName: String,
            deckId: DeckId,
            totalCards: Int,
            isFilteredDeck: Boolean,
        ): DeckPickerConfirmDeleteDeckDialog {
            val f = DeckPickerConfirmDeleteDeckDialog()
            val args = Bundle()
            args.putString("deckName", deckName)
            args.putLong("deckId", deckId)
            args.putInt("totalCards", totalCards)
            args.putBoolean("isFilteredDeck", isFilteredDeck)
            f.arguments = args
            return f
        }
    }
}
