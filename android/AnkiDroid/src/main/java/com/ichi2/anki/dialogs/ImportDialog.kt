// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.annotation.CheckResult
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.R
import com.ichi2.anki.dialogs.ImportDialog.Type.DIALOG_IMPORT_ADD_CONFIRM
import com.ichi2.anki.dialogs.ImportDialog.Type.DIALOG_IMPORT_REPLACE_CONFIRM
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import timber.log.Timber
import java.net.URLDecoder

class ImportDialog : AsyncDialogFragment() {
    interface ImportDialogListener {
        fun importAdd(importPath: String)

        fun importReplace(importPath: String)
    }

    private val dialogType: Type
        get() = Type.fromCode(requireArguments().getInt(IMPORT_DIALOG_TYPE_KEY))

    private val packagePath: String
        get() = requireArguments().getString(IMPORT_DIALOG_PACKAGE_PATH_KEY)!!

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val dialog = AlertDialog.Builder(requireActivity())
        dialog.setCancelable(true)
        val displayFileName = filenameFromPath(convertToDisplayName(packagePath))

        return when (dialogType) {
            DIALOG_IMPORT_ADD_CONFIRM -> {
                dialog
                    .setTitle(R.string.import_title)
                    .setMessage(res().getString(R.string.import_dialog_message_add, displayFileName))
                    .positiveButton(R.string.import_message_add) {
                        (activity as ImportDialogListener).importAdd(packagePath)
                        activity?.dismissAllDialogFragments()
                    }.negativeButton(R.string.dialog_cancel)
                    .create()
            }
            DIALOG_IMPORT_REPLACE_CONFIRM -> {
                dialog
                    .setTitle(R.string.import_title)
                    .setMessage(res().getString(R.string.import_message_replace_confirm, displayFileName))
                    .positiveButton(R.string.dialog_positive_replace) {
                        (activity as ImportDialogListener).importReplace(packagePath)
                        activity?.dismissAllDialogFragments()
                    }.negativeButton(R.string.dialog_cancel)
                    .create()
            }
        }
    }

    private fun convertToDisplayName(name: String): String {
        // ImportUtils URLEncodes names, which isn't great for display.
        // NICE_TO_HAVE: Pass in the DisplayFileName closer to the source of the bad data, rather than fixing it here.
        return try {
            URLDecoder.decode(name, "UTF-8")
        } catch (e: Exception) {
            Timber.w(e, "Failed to convert filename to displayable string")
            name
        }
    }

    override val notificationMessage: String
        get() {
            return res().getString(R.string.import_interrupted)
        }

    override val notificationTitle: String
        get() {
            return res().getString(R.string.import_title)
        }

    enum class Type(
        val code: Int,
    ) {
        DIALOG_IMPORT_ADD_CONFIRM(0),
        DIALOG_IMPORT_REPLACE_CONFIRM(1),
        ;

        companion object {
            fun fromCode(code: Int) = Type.entries.first { code == it.code }
        }
    }

    companion object {
        const val IMPORT_DIALOG_TYPE_KEY = "dialogType"
        const val IMPORT_DIALOG_PACKAGE_PATH_KEY = "packagePath"

        /**
         * A set of dialogs which deal with importing a file
         *
         * @param dialogType An integer which specifies which of the sub-dialogs to show
         * @param packagePath the path of the package to import
         */
        @CheckResult
        fun newInstance(
            dialogType: Type,
            packagePath: String,
        ): ImportDialog =
            ImportDialog().apply {
                arguments =
                    Bundle().apply {
                        putInt(IMPORT_DIALOG_TYPE_KEY, dialogType.code)
                        putString(IMPORT_DIALOG_PACKAGE_PATH_KEY, packagePath)
                    }
            }

        private fun filenameFromPath(path: String): String = path.split("/").toTypedArray()[path.split("/").toTypedArray().size - 1]
    }
}
