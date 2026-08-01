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
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel.ExportReadyParams
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import timber.log.Timber

class ExportReadyDialog : DialogFragment() {
    private val exportPath
        get() = requireArguments().requireString(KEY_EXPORT_PATH)
    private val asText: Boolean
        get() = requireArguments().getBoolean(ARG_SHARE_AS_TEXT, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val dialog = AlertDialog.Builder(requireActivity())

        dialog
            .setTitle(getString(R.string.export_ready_title))
            .positiveButton(R.string.export_choice_save_to) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_EXPORT_SAVE,
                    Bundle().apply { putString(KEY_EXPORT_PATH, exportPath) },
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

/**
 * Handles the last part of the export process where we show the [ExportReadyDialog] fragment. The
 * method will either show the dialog if possible or save it to show it later.
 */
internal fun AnkiActivity.handleExportReadyRequest(params: ExportReadyParams) {
    runCatching {
        Timber.i("Attempting to show ExportReadyDialog...")
        val dialog = ExportReadyDialog.newInstance(params.exportPath, params.asText)
        dialog.show(supportFragmentManager, "ExportReadyDialog")
    }.onFailure { exception ->
        if (exception !is IllegalStateException) throw exception
        Timber.w(
            exception,
            "Failed to show ExportReadyDialog, activity is likely paused.",
        )
        // TODO the previous code was showing a notification here which allowed the user to come
        //  back to the activity, after the main ui refactor see if this is more feasible to implement
    }.onSuccess {
        Timber.i("ExportReadyDialog is displayed, clearing any stored requests...")
        exportReadyViewModel.clearExportReadyRequest()
    }
}
