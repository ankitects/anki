/*
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
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
import com.ichi2.anki.R
import com.ichi2.anki.analytics.AnalyticsDialogFragment
import com.ichi2.anki.analytics.UsageAnalytics
import com.ichi2.anki.utils.ext.dismissAllDialogFragments
import com.ichi2.utils.cancelable
import com.ichi2.utils.checkBoxPrompt
import com.ichi2.utils.create
import com.ichi2.utils.getCheckBoxPrompt
import com.ichi2.utils.message
import com.ichi2.utils.positiveButton
import com.ichi2.utils.title

class DeckPickerAnalyticsOptInDialog : AnalyticsDialogFragment() {
    override fun onCreateDialog(savedInstanceState: Bundle?): AlertDialog {
        super.onCreateDialog(savedInstanceState)
        return AlertDialog.Builder(requireActivity()).create {
            title(R.string.analytics_dialog_title)
            message(R.string.analytics_summ)
            checkBoxPrompt(R.string.analytics_title, isCheckedDefault = false) {}
            positiveButton(R.string.dialog_continue) {
                UsageAnalytics.isEnabled = (it as AlertDialog).getCheckBoxPrompt().isChecked
                activity?.dismissAllDialogFragments()
            }
            cancelable(true)
            setOnCancelListener { activity?.dismissAllDialogFragments() }
        }
    }

    companion object {
        fun newInstance(): DeckPickerAnalyticsOptInDialog = DeckPickerAnalyticsOptInDialog()
    }
}
