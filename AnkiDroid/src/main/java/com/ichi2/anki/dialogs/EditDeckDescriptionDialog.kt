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
 */

package com.ichi2.anki.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.StudyOptionsFragment
import com.ichi2.anki.databinding.DialogDeckDescriptionBinding
import com.ichi2.anki.dialogs.EditDeckDescriptionDialogViewModel.DismissType
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.utils.AndroidUiUtils.hideKeyboard
import com.ichi2.utils.AndroidUiUtils.setFocusAndOpenKeyboard
import com.ichi2.utils.create
import com.ichi2.utils.handleOutsideTouch
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Allows a user to edit the [deck description][com.ichi2.anki.libanki.Deck.description]
 *
 * This is visible on [StudyOptionsFragment]
 */
class EditDeckDescriptionDialog : DialogFragment() {
    private val viewModel: EditDeckDescriptionDialogViewModel by viewModels()

    private lateinit var binding: DialogDeckDescriptionBinding

    private lateinit var alertDialog: AlertDialog

    private var isKeyboardVisible: Boolean = false

    private val onUnsavedChangesBackCallback =
        object : OnBackPressedCallback(enabled = false) {
            override fun handleOnBackPressed() {
                if (isKeyboardVisible) {
                    binding.deckDescriptionInput.hideKeyboard()
                } else {
                    showDiscardChangesDialog()
                }
            }
        }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        this.binding = DialogDeckDescriptionBinding.inflate(layoutInflater)
        return MaterialAlertDialogBuilder(requireContext())
            .create {
                setView(binding.root)
                positiveButton(R.string.save)
                negativeButton(R.string.close)
            }.apply {
                alertDialog = this
                setOnShowListener {
                    positiveButton.setOnClickListener { viewModel.saveAndExit() }
                    negativeButton.setOnClickListener { viewModel.onBackRequested() }
                    handleOutsideTouch(binding) { viewModel.onBackRequested() }
                }
                setCanceledOnTouchOutside(false)
                setCancelable(false)
                onBackPressedDispatcher.addCallback(this, onUnsavedChangesBackCallback)
                show()
                setupDialogView(binding.root)
            }
    }

    override fun setupDialog(
        dialog: Dialog,
        style: Int,
    ) {
        super.setupDialog(dialog, style)
        dialog.window?.let {
            ViewCompat.setOnApplyWindowInsetsListener(it.decorView) { _, insets ->
                isKeyboardVisible = insets.isVisible(WindowInsetsCompat.Type.ime())
                insets
            }
        }
    }

    private fun setupDialogView(view: View) {
        binding.deckDescriptionInput.apply {
            doOnTextChanged { text, _, _, _ ->
                viewModel.description = text?.toString() ?: ""
            }
        }

        binding.formatAsMarkdownInput.apply {
            setOnCheckedChangeListener { _, value -> viewModel.formatAsMarkdown = value }
        }

        // setup 'Format as Markdown' help
        binding.markdownFormattingHelp.apply {
            contentDescription =
                getString(R.string.help_button_content_description, getString(R.string.format_deck_description_as_markdown))
            setOnClickListener {
                MaterialAlertDialogBuilder(requireContext()).show {
                    setTitle(binding.formatAsMarkdownInput.text)
                    setIcon(R.drawable.ic_help_black_24dp)
                    // FIXME: the upstream string unexpectedly contains newlines
                    setMessage(TR.deckConfigDescriptionNewHandlingHint().replace("\n", " ").replace("  ", " "))
                }
            }
        }

        with(binding.deckDescriptionInput) {
            doAfterTextChanged {
                // avoid an additional layout pass in the same frame as
                // TextInputLayout's internal requestLayout(), which causes shaking
                (this.parent as? View)?.post { requestLayout() }
            }
        }

        setupFlows()
    }

    private fun setupFlows() {
        lifecycleScope.launch {
            viewModel.flowOfDismissDialog
                .filterNotNull()
                .collect { dismissType ->
                    when (dismissType) {
                        DismissType.ClosedWithoutSaving -> requireActivity().dismissAllDialogFragments()
                        DismissType.Saved -> {
                            requireActivity().dismissAllDialogFragments()
                            showSnackbar(R.string.deck_description_saved)
                            // notify DeckPicker to invalidate its toolbar menu, otherwise the undo
                            // action to revert the description change is not going to be visible
                            // when there are no other undo actions
                            requireActivity().invalidateOptionsMenu()
                        }
                    }
                }
        }

        lifecycleScope.launch {
            viewModel.flowOfDescription.collect { desc ->
                if (desc == binding.deckDescriptionInput.text.toString()) return@collect
                binding.deckDescriptionInput.setText(desc)
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfFormatAsMarkdown.collect {
                binding.formatAsMarkdownInput.isChecked = it
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfInitCompleted.collect {
                if (!it) return@collect
                binding.toolbar.title = viewModel.windowTitle
                setFocusAndOpenKeyboard(binding.deckDescriptionInput) {
                    binding.deckDescriptionInput.moveCursorToEnd()
                }
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfShowDiscardChanges.collect {
                showDiscardChangesDialog()
            }
        }

        lifecycleScope.launch {
            viewModel.flowOfHasChanges.collect {
                alertDialog.positiveButton.isEnabled = it
                onUnsavedChangesBackCallback.isEnabled = it
            }
        }
    }

    fun showDiscardChangesDialog() {
        Timber.i("asking if user should discard changes")
        DiscardChangesDialog.showDialog(requireContext()) {
            viewModel.closeWithoutSaving()
        }
    }

    companion object {
        fun newInstance(deckId: DeckId): EditDeckDescriptionDialog =
            EditDeckDescriptionDialog().apply {
                arguments =
                    Bundle().apply {
                        putLong(EditDeckDescriptionDialogViewModel.ARG_DECK_ID, deckId)
                    }
            }
    }
}
