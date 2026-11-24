// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.dialogs

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CommonString
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber
import java.util.function.Consumer
import com.ichi2.anki.common.android.R as CommonR

// TODO: Pass optional validation condition i.e. Positive button not enabled if condition is true
open class IntegerDialog : AnalyticsDialogFragment() {
    private var consumer: Consumer<Int>? = null

    fun setCallbackRunnable(consumer: Consumer<Int>?) {
        this.consumer = consumer
    }

    /** use named arguments with this method for clarity */
    fun setArgs(
        title: String,
        prompt: String?,
        digits: Int,
        content: String? = null,
        defaultValue: String? = null,
    ) {
        val args = Bundle()
        args.putString("title", title)
        args.putString("prompt", prompt)
        args.putInt("digits", digits)
        args.putString("content", content)
        args.putString("defaultValue", defaultValue)
        arguments = args
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreateDialog(savedInstanceState)
        return AlertDialog
            .Builder(requireActivity())
            .show {
                title(text = requireArguments().getString("title"))
                positiveButton(CommonString.dialog_ok)
                negativeButton(CommonString.dialog_cancel)
                setMessage(requireArguments().getString("content"))
                setView(CommonR.layout.dialog_generic_text_input)
            }.input(
                hint = requireArguments().getString("prompt"),
                inputType = InputType.TYPE_CLASS_NUMBER,
                maxLength = requireArguments().getInt("digits"),
                prefill = requireArguments().getString("defaultValue"),
                displayKeyboard = true,
            ) { _, text: CharSequence ->
                // #18504: IME bugs can allow a user to send in a non-integer
                val input =
                    try {
                        text.toString().toInt()
                    } catch (e: Exception) {
                        Timber.w(e)
                        // TODO: find a good place in the foreground to show snackbar
                        showSnackbar(CommonString.something_wrong)
                        return@input
                    }

                consumer!!.accept(input)
                dismiss()
            }
    }
}
