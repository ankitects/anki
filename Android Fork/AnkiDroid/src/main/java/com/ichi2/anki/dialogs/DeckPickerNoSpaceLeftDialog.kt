// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.annotation.MainThread
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentActivity
import com.ichi2.anki.CommonString
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.utils.cancelable
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

class DeckPickerNoSpaceLeftDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?) =
        AlertDialog.Builder(requireActivity()).create {
            title(CommonString.storage_full_title)
            message(CommonString.backup_deck_no_storage_left)
            cancelable(true)
            positiveButton(CommonString.dialog_ok) {}
            setOnCancelListener {}
        }

    companion object {
        private const val TAG = "DeckPickerNoSpaceLeftDialog"

        @MainThread
        fun show(activity: FragmentActivity) {
            val manager = activity.supportFragmentManager
            if (manager.findFragmentByTag(TAG) != null) return
            DeckPickerNoSpaceLeftDialog().showNow(manager, TAG)
        }
    }
}
