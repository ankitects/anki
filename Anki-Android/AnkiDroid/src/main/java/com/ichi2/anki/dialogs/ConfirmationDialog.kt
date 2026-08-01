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

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.ifNullOrEmpty
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

/**
 * This is a reusable convenience class which makes it easy to show a confirmation dialog as a DialogFragment.
 * Create a new instance, call setArgs(...), setConfirm(), and setCancel() then show it via the fragment manager as usual.
 */
class ConfirmationDialog : DialogFragment() {
    private val message: String by lazy {
        requireArguments().requireString(ARG_MESSAGE)
    }

    private val title: String
        get() =
            requireArguments()
                .getString(ARG_TITLE)
                .ifNullOrEmpty { requireActivity().getString(R.string.app_name) }

    private val positiveButtonText: String
        get() =
            requireArguments()
                .getString(ARG_POSITIVE_BUTTON_TEXT)
                .ifNullOrEmpty { getString(R.string.dialog_ok) }

    private var confirm = Runnable {} // Do nothing by default
    private var cancel = Runnable {} // Do nothing by default

    /**
     * Sets the message to display. Using [R.string.app_name] as the title.
     */
    fun setArgs(message: String) {
        setArgs(
            title = "",
            message = message,
            positiveButtonText = null,
        )
    }

    fun setArgs(
        title: String?,
        message: String,
        positiveButtonText: String? = null,
    ) {
        arguments =
            Bundle().apply {
                putString(ARG_MESSAGE, message)
                putString(ARG_TITLE, title)
                putString(ARG_POSITIVE_BUTTON_TEXT, positiveButtonText)
            }
    }

    fun setConfirm(confirm: Runnable) {
        this.confirm = confirm
    }

    fun setCancel(cancel: Runnable) {
        this.cancel = cancel
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog =
        AlertDialog.Builder(requireContext()).create {
            title(text = title)
            message(text = message)
            positiveButton(text = positiveButtonText) { confirm.run() }
            negativeButton(R.string.dialog_cancel) { cancel.run() }
        }

    companion object {
        /** The dialog message (required) */
        private const val ARG_MESSAGE = "message"

        /**
         * Optional dialog title. Default: [R.string.app_name]
         */
        private const val ARG_TITLE = "title"

        /** Optional text for the positive button. Default: "OK" */
        private const val ARG_POSITIVE_BUTTON_TEXT = "positiveButtonText"
    }
}
