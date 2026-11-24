// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2015 Timothy Rae <perceptualchaos2@gmail.com>

package com.ichi2.anki.dialogs

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CommonString
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.utils.create
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

class DeckPickerBackupNoSpaceLeftDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        val res = resources
        val space = BackupManager.getFreeDiscSpace(CollectionHelper.getCollectionPath(requireActivity()))
        return AlertDialog
            .Builder(requireContext())
            .create {
                title(CommonString.storage_almost_full_title)
                message(text = res.getString(CommonString.storage_warning, space / 1024 / 1024))
                positiveButton(CommonString.dialog_ok) {
                    (activity as DeckPicker).finish()
                }
            }.apply {
                setCanceledOnTouchOutside(false)
            }
    }

    companion object {
        fun newInstance(): DeckPickerBackupNoSpaceLeftDialog = DeckPickerBackupNoSpaceLeftDialog()
    }
}
