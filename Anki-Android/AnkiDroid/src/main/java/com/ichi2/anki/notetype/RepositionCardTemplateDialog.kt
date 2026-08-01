/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.notetype

import android.content.Context
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show

class RepositionCardTemplateDialog {
    companion object {
        fun showInstance(
            context: Context,
            numberOfTemplates: Int,
            result: (Int) -> Unit,
        ) {
            var displayedDialog: AlertDialog? = null

            displayedDialog =
                AlertDialog
                    .Builder(context)
                    .show {
                        positiveButton(R.string.dialog_ok) {
                            result(
                                displayedDialog!!
                                    .getInputField()
                                    .text
                                    .toString()
                                    .toInt(),
                            )
                        }
                        negativeButton(R.string.dialog_cancel)
                        setMessage(CollectionManager.TR.cardTemplatesEnterNewCardPosition1(numberOfTemplates))
                        setView(R.layout.dialog_generic_text_input)
                    }.input(
                        inputType = InputType.TYPE_CLASS_NUMBER,
                        displayKeyboard = true,
                        waitForPositiveButton = false,
                    ) { dialog, text: CharSequence ->
                        val number = text.toString().toIntOrNull()
                        if (number == null || number < 1 || number > numberOfTemplates) {
                            dialog.positiveButton.isEnabled = false
                            return@input
                        }
                        dialog.positiveButton.isEnabled = true
                    }
        }
    }
}
