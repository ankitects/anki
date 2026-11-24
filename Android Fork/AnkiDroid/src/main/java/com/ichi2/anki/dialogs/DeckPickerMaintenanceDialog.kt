// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.requireString
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

/** Keeps a maintenance decision pending until the user confirms or dismisses it. */
class DeckPickerMaintenanceDialog : DialogFragment() {
    enum class Check(
        val requestKey: String,
    ) {
        MEDIA("request_check_media"),
        DATABASE("request_check_database"),
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val check = Check.valueOf(requireArguments().requireString(ARG_CHECK))
        return AlertDialog.Builder(requireContext()).create {
            when (check) {
                Check.MEDIA -> {
                    title(text = TR.sentenceCase.checkMediaTitle)
                    message(CommonString.check_media_warning)
                }
                Check.DATABASE -> {
                    title(text = TR.sentenceCase.checkDatabase)
                    message(text = requireArguments().requireString(ARG_WARNING))
                }
            }
            positiveButton(if (check == Check.MEDIA) CommonString.dialog_ok else CommonString.integrity_check_continue_anyway) {
                parentFragmentManager.setFragmentResult(check.requestKey, Bundle())
            }
            negativeButton(CommonString.dialog_cancel)
        }
    }

    companion object {
        const val TAG = "maintenanceConfirmation"
        private const val ARG_CHECK = "check"
        private const val ARG_WARNING = "warning"

        fun newInstance(
            check: Check,
            warning: String? = null,
        ) = DeckPickerMaintenanceDialog().apply {
            arguments =
                Bundle().apply {
                    putString(ARG_CHECK, check.name)
                    putString(ARG_WARNING, warning)
                }
        }
    }
}
