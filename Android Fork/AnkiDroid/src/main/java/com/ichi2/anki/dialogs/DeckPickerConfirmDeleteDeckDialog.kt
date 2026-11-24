// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.core.text.HtmlCompat
import com.ichi2.anki.CommonPlurals
import com.ichi2.anki.CommonString
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
                resources.getString(CommonString.delete_cram_deck_message, "<b>$deckName</b>")
            } else {
                resources.getQuantityString(
                    CommonPlurals.delete_deck_message,
                    totalCards,
                    "<b>$deckName</b>",
                    totalCards,
                )
            }
        super.onCreate(savedInstanceState)
        return AlertDialog
            .Builder(requireActivity())
            .setTitle(CommonString.delete_deck_title)
            .setMessage(
                HtmlCompat.fromHtml(
                    message,
                    HtmlCompat.FROM_HTML_MODE_LEGACY,
                ),
            ).setIcon(R.drawable.ic_warning)
            .setPositiveButton(CommonString.dialog_positive_delete) { _, _ ->
                (activity as DeckPicker).deleteDeck(deckId)
                activity?.dismissAllDialogFragments()
            }.setNegativeButton(CommonString.dialog_cancel) { _, _ ->
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
