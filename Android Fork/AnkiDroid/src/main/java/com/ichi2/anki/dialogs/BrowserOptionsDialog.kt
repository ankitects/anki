// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Akshit Sinha <akshitsinha3@gmail.com>

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.text.Editable
import androidx.appcompat.app.AppCompatDialogFragment
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnSelectionFragment
import com.ichi2.anki.browser.CardBrowserViewModel
import com.ichi2.anki.databinding.DialogBrowserOptionsBinding
import com.ichi2.anki.model.CardsOrNotes
import com.ichi2.anki.settings.Prefs
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
        viewModel.setCardsOrNotes(dialogCardsOrNotes)
        viewModel.setTruncated(binding.truncateCheckBox.isChecked)
        viewModel.setIgnoreAccents(binding.ignoreAccentsCheckBox.isChecked)
        viewModel.setDefaultSearchText(binding.defaultSearchText.text.orEmpty())
    }

    private val cardsOrNotes by lazy {
        when (arguments?.getBoolean(ARG_CARDS_OR_NOTES)) {
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
        arguments?.getBoolean(ARG_IS_TRUNCATED) ?: run {
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
        binding.flagTitle.text = TR.browsingFlag()

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

        if (Prefs.devUsingCardBrowserSearchView) {
            binding.defaultSearchInputLayout.apply {
                isVisible = true
                hint = TR.preferencesDefaultSearchText()
                placeholderText = TR.preferencesDefaultSearchTextExample()
            }
            binding.defaultSearchText.setText(viewModel.defaultBrowserSearch)
        }

        binding.browsingTextView.text = TR.preferencesBrowsing()

        return MaterialAlertDialogBuilder(requireContext()).create {
            setView(binding.root)
            setTitle(TR.sentenceCase.browserOptions)
            positiveButton(CommonString.save) { saveChanges() }
            negativeButton(CommonString.dialog_cancel)
        }
    }

    /** Opens [BrowserColumnSelectionFragment] for the current selection of [CardsOrNotes] */
    private fun openColumnManager() {
        val dialog = BrowserColumnSelectionFragment.createInstance(viewModel.cardsOrNotes)
        dialog.show(requireActivity().supportFragmentManager, null)
    }

    companion object {
        private const val ARG_CARDS_OR_NOTES = "cardsOrNotes"
        private const val ARG_IS_TRUNCATED = "isTruncated"

        fun newInstance(
            cardsOrNotes: CardsOrNotes,
            isTruncated: Boolean,
        ): BrowserOptionsDialog {
            Timber.i("BrowserOptionsDialog::newInstance")
            return BrowserOptionsDialog().apply {
                arguments =
                    Bundle().apply {
                        putBoolean(ARG_CARDS_OR_NOTES, cardsOrNotes == CardsOrNotes.CARDS)
                        putBoolean(ARG_IS_TRUNCATED, isTruncated)
                    }
            }
        }
    }
}

/** Returns the text content as a [String], or `""` if the receiver is `null`. */
private fun Editable?.orEmpty(): String = this?.toString().orEmpty()
