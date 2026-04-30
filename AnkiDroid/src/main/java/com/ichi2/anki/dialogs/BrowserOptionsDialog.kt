/*
 *  Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>
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

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.os.bundleOf
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.databinding.DialogBrowserOptionsBinding
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.create
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import timber.log.Timber

class BrowserOptionsDialog : AppCompatDialogFragment(R.layout.dialog_browser_options) {
    private val viewModel: CardBrowserViewModel by activityViewModels()

    private lateinit var binding: DialogBrowserOptionsBinding

    /** The unsaved value of [CardsOrNotes] */
    private val dialogCardsOrNotes: CardsOrNotes
        get() {
            return when (binding.selectBrowserMode.checkedRadioButtonId) {
                R.id.select_cards_mode -> CardsOrNotes.CARDS
                else -> CardsOrNotes.NOTES
            }
        }

    /** Persists updated options to the ViewModel */
    fun saveChanges() {
        if (cardsOrNotes != dialogCardsOrNotes) {
            viewModel.setCardsOrNotes(dialogCardsOrNotes)
        }
        val newTruncate = binding.truncateCheckBox.isChecked

        if (newTruncate != isTruncated) {
            viewModel.setTruncated(newTruncate)
        }

        val newIgnoreAccent = binding.ignoreAccentsCheckBox.isChecked
        if (newIgnoreAccent != viewModel.shouldIgnoreAccents) {
            viewModel.setIgnoreAccents(newIgnoreAccent)
        }
    }

    private val cardsOrNotes by lazy {
        when (arguments?.getBoolean(CARDS_OR_NOTES_KEY)) {
            true -> CardsOrNotes.CARDS
            false -> CardsOrNotes.NOTES
            null -> {
                // Default case, and what we'll do if there were no arguments supplied
                Timber.w("BrowserOptionsDialog instantiated without configuration.")
                CardsOrNotes.CARDS
            }
        }
    }

    private val isTruncated by lazy {
        arguments?.getBoolean(IS_TRUNCATED_KEY) ?: run {
            Timber.w("BrowserOptionsDialog instantiated without configuration.")
            false
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        binding = DialogBrowserOptionsBinding.inflate(layoutInflater)

        if (cardsOrNotes == CardsOrNotes.CARDS) {
            binding.selectCardsMode.isChecked = true
        } else {
            binding.selectNotesMode.isChecked = true
        }

        binding.truncateCheckBox.isChecked = isTruncated
        binding.toggleCardsNotesTitle.text = TR.sentenceCase.toggleCardsNotes

        binding.renameFlag.setOnClickListener {
            Timber.d("Rename flag clicked")
            val flagRenameDialog = FlagRenameDialog()
            flagRenameDialog.show(parentFragmentManager, "FlagRenameDialog")
            dismiss()
        }

        binding.manageColumnsButton.setOnClickListener {
            openColumnManager()
        }

        binding.ignoreAccentsCheckBox.apply {
            text = TR.preferencesIgnoreAccentsInSearch()
            isChecked = viewModel.shouldIgnoreAccents
        }

        binding.browsingTextView.text = TR.preferencesBrowsing()

        return MaterialAlertDialogBuilder(requireContext()).create {
            setView(binding.root)
            setTitle(getString(R.string.browser_options_dialog_heading))
            positiveButton(R.string.save) { saveChanges() }
            negativeButton(R.string.dialog_cancel)
        }
    }

    /** Opens [BrowserColumnSelectionFragment] for the current selection of [CardsOrNotes] */
    private fun openColumnManager() {
        val dialog = BrowserColumnSelectionFragment.createInstance(viewModel.cardsOrNotes)
        dialog.show(requireActivity().supportFragmentManager, null)
    }

    companion object {
        private const val CARDS_OR_NOTES_KEY = "cardsOrNotes"
        private const val IS_TRUNCATED_KEY = "isTruncated"

        fun newInstance(
            cardsOrNotes: CardsOrNotes,
            isTruncated: Boolean,
        ): BrowserOptionsDialog {
            Timber.i("BrowserOptionsDialog::newInstance")
            return BrowserOptionsDialog().apply {
                arguments =
                    bundleOf(
                        CARDS_OR_NOTES_KEY to (cardsOrNotes == CardsOrNotes.CARDS),
                        IS_TRUNCATED_KEY to isTruncated,
                    )
            }
        }
    }
}
