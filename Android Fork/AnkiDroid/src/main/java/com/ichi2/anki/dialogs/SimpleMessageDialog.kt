// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.utils.create

class SimpleMessageDialog : AsyncDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreateDialog(savedInstanceState)
        return AlertDialog.Builder(requireContext()).create {
            setTitle(notificationTitle)
            setMessage(notificationMessage)
            setPositiveButton(CommonString.dialog_ok) { _, _ ->
                activity?.dismissSimpleMessageDialog(requireArguments().getBoolean(ARG_RELOAD))
            }
        }
    }

    /**
     * Handle closing simple message dialog
     * @param reload loads the [DeckPicker] after dismissing the dialogs
     */
    fun FragmentActivity.dismissSimpleMessageDialog(reload: Boolean) {
        dismissAllDialogFragments()
        if (reload) {
            val deckPicker = Intent(this, DeckPicker::class.java)
            deckPicker.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(deckPicker)
        }
    }

    override val notificationTitle: String
        get() {
            val title = requireArguments().getString(ARG_TITLE)!!
            return if ("" != title) {
                title
            } else {
                AnkiDroidApp.appResources.getString(R.string.app_name)
            }
        }

    override val notificationMessage: String?
        get() {
            return requireArguments().getString(ARG_MESSAGE)
        }

    companion object {
        /** The title of the notification/dialog */
        private const val ARG_TITLE = "arg_title"

        /** The content of the notification/dialog */
        private const val ARG_MESSAGE = "arg_message"

        /**
         * If the calling activity should be reloaded when 'OK' is pressed.
         * @see dismissSimpleMessageDialog
         */
        private const val ARG_RELOAD = "arg_reload"

        fun newInstance(
            title: String,
            message: String?,
            reload: Boolean,
        ): SimpleMessageDialog {
            val f = SimpleMessageDialog()
            val args = Bundle()
            args.putString(ARG_TITLE, title)
            args.putString(ARG_MESSAGE, message)
            args.putBoolean(ARG_RELOAD, reload)
            f.arguments = args
            return f
        }
    }
}
