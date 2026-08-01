/*
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
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

import android.os.Bundle
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.snackbar.showSnackbar
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import timber.log.Timber
import java.util.function.Consumer

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
                positiveButton(R.string.dialog_ok)
                negativeButton(R.string.dialog_cancel)
                setMessage(requireArguments().getString("content"))
                setView(R.layout.dialog_generic_text_input)
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
                        showSnackbar(R.string.something_wrong)
                        return@input
                    }

                consumer!!.accept(input)
                dismiss()
            }
    }
}
