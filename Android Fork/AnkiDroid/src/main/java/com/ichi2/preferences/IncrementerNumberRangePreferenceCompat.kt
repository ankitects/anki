// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Tushar Bhatt <tbhatt312@gmail.com>

package com.ichi2.preferences

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.core.widget.doAfterTextChanged
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.databinding.DialogIncrementerPreferenceBinding
import com.ichi2.utils.NonLeadingZeroInputFilter
import com.ichi2.utils.moveCursorToEnd
import com.ichi2.utils.positiveButton

/** Marker class to be used in preferences */
class IncrementerNumberRangePreferenceCompat :
    NumberRangePreferenceCompat,
    DialogFragmentProvider {
    @Suppress("unused")
    constructor(
        context: Context,
        attrs: AttributeSet?,
        defStyleAttr: Int,
        defStyleRes: Int,
    ) : super(context, attrs, defStyleAttr, defStyleRes)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    @Suppress("unused")
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    @Suppress("unused")
    constructor(context: Context) : super(context)

    class IncrementerNumberRangeDialogFragmentCompat : NumberRangeDialogFragmentCompat() {
        private var bindingRef: DialogIncrementerPreferenceBinding? = null
        private val binding get() = bindingRef!!
        private var lastValidEntry = 0

        // Reference to the system OK button
        private var positiveButton: Button? = null

        /**
         * Sets [.mEditText] width and gravity.
         */
        override fun onBindDialogView(view: View) {
            super.onBindDialogView(view)

            lastValidEntry =
                try {
                    editText.text.toString().toInt()
                } catch (nfe: NumberFormatException) {
                    // This should not be possible but just in case, recover with a valid minimum from superclass
                    numberRangePreference.min
                }
            editText.filters += NonLeadingZeroInputFilter
            // Validate the final value, not individual character changes
            editText.doAfterTextChanged { updateButtonState() }

            // Initial check to set correct button states on open
            updateButtonState()
        }

        override fun onStart() {
            super.onStart()
            positiveButton = (dialog as? androidx.appcompat.app.AlertDialog)?.positiveButton
            positiveButton?.text = TR.actionsSave()

            // Rerun validation now that we have the OK button reference
            updateButtonState()
        }

        /**
         * Sets appropriate Text and OnClickListener for buttons
         *
         * Sets orientation for layout
         */
        override fun onCreateDialogView(context: Context): View {
            bindingRef = DialogIncrementerPreferenceBinding.inflate(LayoutInflater.from(context))

            binding.incrementButton.setOnClickListener { updateEditText(true) }
            binding.decrementButton.setOnClickListener { updateEditText(false) }

            return binding.root
        }

        /**
         * Increments/Decrements the value of [.mEditText] by 1 based on the parameter value.
         *
         * @param isIncrement Indicator for whether to increase or decrease the value.
         */
        private fun updateEditText(isIncrement: Boolean) {
            var value: Int =
                try {
                    editText.text.toString().toInt()
                } catch (e: NumberFormatException) {
                    // If the user entered a non-number then incremented, restore to a good value
                    lastValidEntry
                }
            value = if (isIncrement) value + 1 else value - 1

            // Make sure value is within range
            lastValidEntry = numberRangePreference.getValidatedRangeFromInt(value)
            editText.setText(lastValidEntry.toString())
            editText.moveCursorToEnd()

            updateButtonState()
        }

        override fun onDestroyView() {
            super.onDestroyView()
            bindingRef = null
            positiveButton = null
        }

        /**
         * Validates the current input and updates the state of buttons
         *
         * Displays specific error messages for overflow and range limits
         */
        private fun updateButtonState() {
            val text = editText.text.toString()
            val value = text.toIntOrNull()

            val result = validate(text, value, numberRangePreference.min, numberRangePreference.max)

            binding.textInputLayout.error = null
            binding.textInputLayout.isErrorEnabled = false

            binding.incrementButton.isEnabled = false
            binding.decrementButton.isEnabled = false
            positiveButton?.isEnabled = false

            when (result) {
                ValidationResult.VALID -> {
                    positiveButton?.isEnabled = true
                    // Even if valid, we might be at the edge of the range, so update +/- buttons
                    if (value != null) {
                        binding.incrementButton.isEnabled = value < numberRangePreference.max
                        binding.decrementButton.isEnabled = value > numberRangePreference.min
                    }
                }
                ValidationResult.OVERFLOW -> {
                    binding.decrementButton.isEnabled = true
                    binding.textInputLayout.error = getString(CommonString.maximum_value_is, numberRangePreference.max)
                }
                ValidationResult.UNDERFLOW -> {
                    binding.incrementButton.isEnabled = true
                    binding.textInputLayout.error = getString(CommonString.minimum_value_is, numberRangePreference.min)
                }
                ValidationResult.INVALID -> {
                    binding.textInputLayout.error = getString(CommonString.invalid_value)
                }
                ValidationResult.EMPTY -> {
                    // Empty input is invalid for submission, but doesn't warrant an error message yet
                }
            }
        }
    }

    companion object {
        enum class ValidationResult {
            VALID,
            INVALID,
            EMPTY,
            OVERFLOW,
            UNDERFLOW,
        }

        /**
         * Validation logic for the preference input.
         *
         * @param text The raw string from the EditText.
         * @param value The parsed integer value (or null if parsing failed).
         * @param min The minimum allowed value.
         * @param max The maximum allowed value.
         * @return A [ValidationResult] representing the state of the input.
         */
        fun validate(
            text: String,
            value: Int?,
            min: Int,
            max: Int,
        ): ValidationResult =
            when {
                text.isEmpty() -> ValidationResult.EMPTY
                value == null -> ValidationResult.INVALID
                value > max -> ValidationResult.OVERFLOW
                value < min -> ValidationResult.UNDERFLOW
                else -> ValidationResult.VALID
            }
    }

    override fun makeDialogFragment() = IncrementerNumberRangeDialogFragmentCompat()
}
