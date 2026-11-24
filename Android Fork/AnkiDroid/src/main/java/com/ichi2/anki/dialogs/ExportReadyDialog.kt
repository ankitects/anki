// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.AnkiActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.dialogs.viewmodel.ExportReadyViewModel.ExportReadyParams
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import timber.log.Timber

class ExportReadyDialog : DialogFragment() {
    private val exportPath
        get() = requireArguments().requireString(ARG_EXPORT_PATH)
    private val asText: Boolean
        get() = requireArguments().getBoolean(ARG_SHARE_AS_TEXT, false)

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val dialog = AlertDialog.Builder(requireActivity())

        dialog
            .setTitle(getString(CommonString.export_ready_title))
            .positiveButton(CommonString.export_choice_save_to) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_EXPORT_SAVE,
                    Bundle().apply { putString(RESULT_EXPORT_PATH, exportPath) },
                )
            }.negativeButton(CommonString.export_choice_share) {
                parentFragmentManager.setFragmentResult(
                    REQUEST_EXPORT_SHARE,
                    Bundle().apply {
                        putString(RESULT_EXPORT_PATH, exportPath)
                        putBoolean(RESULT_SHARE_AS_TEXT, asText)
                    },
                )
            }

        return dialog.create()
    }

    companion object {
        const val REQUEST_EXPORT_SAVE = "request_export_save"
        const val REQUEST_EXPORT_SHARE = "request_export_share"
        const val RESULT_EXPORT_PATH = "key_export_path"
        const val RESULT_SHARE_AS_TEXT = "arg_share_as_text"

        private const val ARG_EXPORT_PATH = "key_export_path"
        private const val ARG_SHARE_AS_TEXT = "arg_share_as_text"

        fun newInstance(
            exportPath: String,
            asText: Boolean = false,
        ) = ExportReadyDialog().apply {
            arguments =
                Bundle().apply {
                    putString(ARG_EXPORT_PATH, exportPath)
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
