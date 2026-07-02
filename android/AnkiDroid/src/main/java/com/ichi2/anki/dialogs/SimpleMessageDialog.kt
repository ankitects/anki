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

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.AnkiDroidApp
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
            setPositiveButton(R.string.dialog_ok) { _, _ ->
                activity?.dismissSimpleMessageDialog(requireArguments().getBoolean(ARGS_RELOAD))
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
            val title = requireArguments().getString(ARGS_TITLE)!!
            return if ("" != title) {
                title
            } else {
                AnkiDroidApp.appResources.getString(R.string.app_name)
            }
        }

    override val notificationMessage: String?
        get() {
            return requireArguments().getString(ARGS_MESSAGE)
        }

    companion object {
        /** The title of the notification/dialog */
        private const val ARGS_TITLE = "title"

        /** The content of the notification/dialog */
        private const val ARGS_MESSAGE = "message"

        /**
         * If the calling activity should be reloaded when 'OK' is pressed.
         * @see dismissSimpleMessageDialog
         */
        private const val ARGS_RELOAD = "reload"

        fun newInstance(
            title: String,
            message: String?,
            reload: Boolean,
        ): SimpleMessageDialog {
            val f = SimpleMessageDialog()
            val args = Bundle()
            args.putString(ARGS_TITLE, title)
            args.putString(ARGS_MESSAGE, message)
            args.putBoolean(ARGS_RELOAD, reload)
            f.arguments = args
            return f
        }
    }
}
