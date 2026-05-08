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
import android.os.Message
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.utils.ext.showDialogFragment
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton

class ExportReadyDialog : AsyncDialogFragment() {
    private val exportPath
        get() = requireArguments().getString(KEY_EXPORT_PATH) ?: error("Missing required argument: exportPath!")
    private val asText: Boolean
        get() = requireArguments().getBoolean(ARG_SHARE_AS_TEXT, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val dialog = AlertDialog.Builder(requireActivity())

        dialog
            .setTitle(notificationTitle)
            .positiveButton(R.string.export_choice_save_to) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_EXPORT_SAVE,
                    bundleOf(KEY_EXPORT_PATH to exportPath),
                )
            }.negativeButton(R.string.export_choice_share) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_EXPORT_SHARE,
                    Bundle().apply {
                        putString(KEY_EXPORT_PATH, exportPath)
                        putBoolean(ARG_SHARE_AS_TEXT, asText)
                    },
                )
            }

        return dialog.create()
    }

    override val notificationTitle: String
        get() = res().getString(R.string.export_ready_title)

    override val notificationMessage: String? = null

    override val dialogHandlerMessage: DialogHandlerMessage
        get() = ExportReadyDialogMessage(exportPath)

    /** Export ready dialog message*/
    class ExportReadyDialogMessage(
        private val exportPath: String,
    ) : DialogHandlerMessage(
            which = WhichDialogHandler.MSG_EXPORT_READY,
            analyticName = "ExportReadyDialog",
        ) {
        override fun handleAsyncMessage(activity: AnkiActivity) {
            // we may be called via any AnkiActivity but export is a DeckPicker thing
            activity
                .requireDeckPickerOrShowError()
                ?.showDialogFragment(newInstance(exportPath))
        }

        override fun toMessage(): Message =
            Message.obtain().apply {
                what = this@ExportReadyDialogMessage.what
                data = bundleOf("exportPath" to exportPath)
            }

        companion object {
            fun fromMessage(message: Message): ExportReadyDialogMessage {
                val exportPath = message.data.getString("exportPath")!!
                return ExportReadyDialogMessage(exportPath)
            }
        }
    }

    companion object {
        const val REQUEST_EXPORT_SAVE = "request_export_save"
        const val REQUEST_EXPORT_SHARE = "request_export_share"
        const val KEY_EXPORT_PATH = "key_export_path"
        const val ARG_SHARE_AS_TEXT = "arg_share_as_text"

        fun newInstance(
            exportPath: String,
            asText: Boolean = false,
        ) = ExportReadyDialog().apply {
            arguments =
                Bundle().apply {
                    putString(KEY_EXPORT_PATH, exportPath)
                    putBoolean(ARG_SHARE_AS_TEXT, asText)
                }
        }
    }
}
