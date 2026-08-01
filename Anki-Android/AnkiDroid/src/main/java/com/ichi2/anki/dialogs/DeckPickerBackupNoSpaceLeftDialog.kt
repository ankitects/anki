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
import com.ichi2.anki.BackupManager
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
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
                title(R.string.storage_almost_full_title)
                message(text = res.getString(R.string.storage_warning, space / 1024 / 1024))
                positiveButton(R.string.dialog_ok) {
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
