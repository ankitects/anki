// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2013 Houssam Salem <houssam.salem.au@gmail.com>

package com.ichi2.preferences

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.AttributeSet
import android.view.View
import android.widget.EditText
import androidx.core.content.withStyledAttributes
import androidx.preference.EditTextPreference
import androidx.preference.EditTextPreferenceDialogFragmentCompat
import com.ichi2.anki.R
import com.ichi2.anki.utils.getFormattedStringOrPlurals
import com.ichi2.utils.numberRangeTitle
import timber.log.Timber

open class NumberRangePreferenceCompat
    @JvmOverloads // fixes: Error inflating class com.ichi2.preferences.NumberRangePreferenceCompat
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = androidx.preference.R.attr.editTextPreferenceStyle,
        defStyleRes: Int = androidx.preference.R.style.Preference_DialogPreference_EditTextPreference,
    ) : EditTextPreference(context, attrs, defStyleAttr, defStyleRes),
        DialogFragmentProvider {
        var defaultValue: String? = null

        var min = 0
            protected set
        var max = 0
            private set

        init {
            context.withStyledAttributes(attrs, R.styleable.NumberRangePreferenceCompat) {
                min = getInt(R.styleable.NumberRangePreferenceCompat_min, 0)
                max = getInt(R.styleable.NumberRangePreferenceCompat_max, Int.MAX_VALUE)
            }
            defaultValue = attrs?.getAttributeValue("http://schemas.android.com/apk/res/android", "defaultValue")

            context.withStyledAttributes(attrs, R.styleable.CustomPreference) {
                getResourceId(R.styleable.CustomPreference_summaryFormat, 0)
                    .takeIf { it != 0 }
                    ?.let { redId ->
                        setSummaryProvider {
                            if (text == null) {
                                return@setSummaryProvider ""
                            }
                            context.getFormattedStringOrPlurals(redId, text!!.toInt())
                        }
                    }
            }
        }

        /** The maximum available number of digits */
        val maxDigits: Int get() = max.toString().length

    /*
     * Since this preference deals with integers only, it makes sense to only store and retrieve integers. However,
     * since it is extending EditTextPreference, the persistence and retrieval methods that are called are for a String
     * type. The two methods below intercept the persistence and retrieval methods for Strings and replaces them with
     * their Integer equivalents.
     */
        override fun getPersistedString(defaultReturnValue: String?): String? = getPersistedInt(getDefaultValue()).toString()

        override fun persistString(value: String): Boolean = persistInt(value.toInt())

        /**
         * Return the integer rounded to the nearest bound if it is outside of the acceptable range.
         *
         * @param input Integer to validate.
         * @return The input value within acceptable range.
         */
        fun getValidatedRangeFromInt(input: Int): Int {
            if (input < min) {
                return min
            } else if (input > max) {
                return max
            }
            return input
        }

        /**
         * Return the string as an int with the number rounded to the nearest bound if it is outside of the acceptable
         * range.
         *
         * @param input User input in text editor.
         * @return The input value within acceptable range.
         */
        fun getValidatedRangeFromString(input: String): Int =
            if (input.isEmpty()) {
                min
            } else {
                try {
                    getValidatedRangeFromInt(input.toInt())
                } catch (e: NumberFormatException) {
                    Timber.w(e)
                    min
                }
            }

        /**
         * Get the persisted value held by this preference.
         *
         * @return the persisted value.
         */
        fun getValue(): Int = getPersistedInt(getDefaultValue())

        private fun getDefaultValue(): Int {
            return try {
                return defaultValue?.toInt() ?: min
            } catch (_: Exception) {
                min
            }
        }

        /**
         * Set this preference's value. The value is validated and persisted as an Integer.
         *
         * @param value to set.
         */
        fun setValue(value: Int) {
            val validated = getValidatedRangeFromInt(value)
            text = validated.toString()
            persistInt(validated)
        }

        /**
         * Set this preference's value. The value is validated and persisted as an Integer.
         *
         * @param value to set.
         */
        fun setValue(value: String) {
            val fromString = getValidatedRangeFromString(value)
            text = fromString.toString()
            persistInt(fromString)
        }

        /**
         * Sets the callback to be invoked when this preference is changed by the user
         * (but before the internal state has been updated) on the internal onPreferenceChangeListener,
         * returning true on it by default
         * @param onPreferenceChangeListener The callback to be invoked
         */
        fun setOnPreferenceChangeListener(onPreferenceChangeListener: (newValue: Int) -> Unit) {
            this.setOnPreferenceChangeListener { _, newValue ->
                if (newValue !is Int) return@setOnPreferenceChangeListener false
                onPreferenceChangeListener(newValue)
                true
            }
        }

        open class NumberRangeDialogFragmentCompat : EditTextPreferenceDialogFragmentCompat() {
            val numberRangePreference: NumberRangePreferenceCompat get() = preference as NumberRangePreferenceCompat

            lateinit var editText: EditText

            override fun onPrepareDialogBuilder(builder: androidx.appcompat.app.AlertDialog.Builder) {
                super.onPrepareDialogBuilder(builder)

                builder.numberRangeTitle(preference.dialogTitle ?: preference.title, requireContext())
            }

            /**
             * Update settings to only allow integer input and set the maximum number of digits allowed in the text field based
             * on the current value of the [.mMax] field.
             */
            override fun onBindDialogView(view: View) {
                editText = view.findViewById(android.R.id.edit)!!

                // Only allow integer input
                editText.inputType = InputType.TYPE_CLASS_NUMBER

                // Clone the existing filters so we don't override them, then append our one at the end.
                editText.filters = arrayOf(*editText.filters, InputFilter.LengthFilter(numberRangePreference.maxDigits))

                super.onBindDialogView(view)
            }

            override fun onDialogClosed(positiveResult: Boolean) {
                // don't change the value if the dialog was cancelled or closed without any text
                if (!positiveResult || editText.text.isEmpty()) {
                    return
                }
                val newValue = numberRangePreference.getValidatedRangeFromString(editText.text.toString())
                if (numberRangePreference.callChangeListener(newValue)) {
                    numberRangePreference.setValue(newValue)
                }
            }
        }

        enum class ShouldShowDialog { Yes, No }

        var onClickListener: () -> ShouldShowDialog = { ShouldShowDialog.Yes }

        override fun onClick() {
            if (onClickListener() == ShouldShowDialog.Yes) {
                super.onClick()
            }
        }

        override fun makeDialogFragment() = NumberRangeDialogFragmentCompat()
    }
