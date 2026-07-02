// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.browser

import android.app.Dialog
import android.content.res.Configuration
import android.os.Bundle
import android.text.InputFilter
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.R
import com.ichi2.anki.databinding.FragmentRepositionCardBinding
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.create
import com.ichi2.utils.customView
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.textAsIntOrNull
import com.ichi2.utils.title

/**
 * Follows desktop implementation to show the same ui with all the options to reposition new
 * (currently selected) cards in [CardBrowser].
 * See https://github.com/ankitects/anki/blob/44e01ea063e6d1b812ace9c001f7ba4a8ccf4479/qt/aqt/forms/reposition.ui#L14
 * See https://github.com/ankitects/anki/blob/1fb1cbbf85c48a54c05cb4442b1b424a529cac60/qt/aqt/operations/scheduling.py#L107
 */
class RepositionCardFragment : DialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = FragmentRepositionCardBinding.inflate(layoutInflater)
        binding.queueLimitsLabel.text =
            """
            ${TR.browsingQueueTop(requireArguments().getInt(ARG_QUEUE_TOP))}
            ${TR.browsingQueueBottom(requireArguments().getInt(ARG_QUEUE_BOTTOM))}
            """.trimIndent()
        binding.startInputLayout.hint =
            TR.browsingStartPosition().removeSuffix(":")
        binding.stepInputLayout.hint =
            TR.browsingStep().removeSuffix(":")

        binding.startInputEditText.requestFocus()
        binding.startInputEditText.selectAll()

        // Match upstream Anki UI limits:
        // - Start position: max 6 digits
        // - Step size: max 4 digits
        // Backend accepts uint32 values (scheduler.proto), but UI is intentionally
        // restricted to match upstream behavior and prevent unrealistic inputs.
        binding.startInputEditText.filters = arrayOf(InputFilter.LengthFilter(6))
        binding.stepInputEditText.filters = arrayOf(InputFilter.LengthFilter(4))

        binding.randomizeOrderCheck.apply {
            text = TR.browsingRandomizeOrder()
            isChecked = requireArguments().getBoolean(ARG_RANDOM)
        }
        binding.shiftPositionCheck.apply {
            text = TR.browsingShiftPositionOfExistingCards()
            isChecked = requireArguments().getBoolean(ARG_SHIFT)
        }
        val title = TR.sentenceCase.repositionNewCards
        val dialog =
            AlertDialog.Builder(requireContext()).create {
                title(text = title)
                customView(binding.root)
                negativeButton(R.string.dialog_cancel)
                positiveButton(text = TR.actionsReposition()) {
                    val position =
                        binding.startInputEditText.textAsIntOrNull() ?: return@positiveButton
                    val step =
                        binding.stepInputEditText.textAsIntOrNull() ?: return@positiveButton
                    setFragmentResult(
                        REQUEST_REPOSITION_NEW_CARDS,
                        Bundle().apply {
                            putInt(ARG_POSITION, position)
                            putInt(ARG_STEP, step)
                            putBoolean(ARG_RANDOM, binding.randomizeOrderCheck.isChecked)
                            putBoolean(ARG_SHIFT, binding.shiftPositionCheck.isChecked)
                        },
                    )
                }
            }

        dialog.setOnShowListener {
            val repositionButton = dialog.getButton(AlertDialog.BUTTON_POSITIVE)

            val editTexts =
                listOf(
                    binding.startInputEditText,
                    binding.stepInputEditText,
                )

            val validate = {
                repositionButton.isEnabled =
                    editTexts.all {
                        it.text
                            ?.toString()
                            ?.trim()
                            ?.isNotEmpty() == true
                    }
            }

            validate()

            editTexts.forEach {
                it.doOnTextChanged { _, _, _, _ -> validate() }
            }
        }

        // Only automatically show the keyboard in portrait mode
        // In landscape mode, the keyboard would take up too much screen space and hide the dialog
        val isPortrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        if (isPortrait) {
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }

        return dialog
    }

    companion object {
        const val REQUEST_REPOSITION_NEW_CARDS = "request_repositions_new_cards"
        private const val ARG_QUEUE_TOP = "arg_queue_top"
        private const val ARG_QUEUE_BOTTOM = "arg_queue_bottom"
        const val ARG_POSITION = "arg_position"
        const val ARG_STEP = "arg_step"
        const val ARG_RANDOM = "arg_random"
        const val ARG_SHIFT = "arg_shift"

        fun newInstance(
            queueTop: Int,
            queueBottom: Int,
            random: Boolean,
            shift: Boolean,
        ) = RepositionCardFragment().apply {
            arguments =
                Bundle().apply {
                    putInt(ARG_QUEUE_TOP, queueTop)
                    putInt(ARG_QUEUE_BOTTOM, queueBottom)
                    putBoolean(ARG_RANDOM, random)
                    putBoolean(ARG_SHIFT, shift)
                }
        }
    }
}
