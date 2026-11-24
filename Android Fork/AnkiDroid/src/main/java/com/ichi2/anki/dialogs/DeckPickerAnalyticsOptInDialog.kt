// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.dialogs

import android.content.DialogInterface
import android.os.Bundle
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.AnkiDroidUsageAnalytics
import com.ichi2.utils.cancelable
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.create
import com.ichi2.utils.getCheckBoxPrompt
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title
import timber.log.Timber

class DeckPickerAnalyticsOptInDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreateDialog(savedInstanceState)
        return AlertDialog.Builder(requireActivity()).create {
            title(CommonString.analytics_dialog_title)
            message(CommonString.analytics_summ)
            checkBoxPrompt(CommonString.analytics_title, isCheckedDefault = false) { isChecked ->
                Timber.d("Analytics opt-in checkbox changed: %b", isChecked)
            }
            positiveButton(CommonString.dialog_continue) {
                val optedIn = (it as AlertDialog).getCheckBoxPrompt().isChecked
                Timber.i("Analytics opt-in dialog confirmed: %b", optedIn)
                AnkiDroidUsageAnalytics.isEnabled = optedIn
            }
            cancelable(true)
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        Timber.i("Analytics opt-in dialog cancelled")
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        Timber.i("Analytics opt-in dialog dismissed")
    }

    companion object {
        private const val TAG = "DeckPickerAnalyticsOptInDialog"

        /** Shows one prompt per activity */
        @MainThread
        fun show(activity: FragmentActivity) {
            val manager = activity.supportFragmentManager
            if (manager.findFragmentByTag(TAG) != null) return

            // Register immediately so repeated calls cannot enqueue duplicate prompts.
            DeckPickerAnalyticsOptInDialog().showNow(manager, TAG)
        }
    }
}
