// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import android.content.Context
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.utils.getInputField
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.anki.common.android.R as CommonR

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
                        positiveButton(CommonString.dialog_ok) {
                            result(
                                displayedDialog!!
                                    .getInputField()
                                    .text
                                    .toString()
                                    .toInt(),
                            )
                        }
                        negativeButton(CommonString.dialog_cancel)
                        setMessage(CollectionManager.TR.cardTemplatesEnterNewCardPosition1(numberOfTemplates))
                        setView(CommonR.layout.dialog_generic_text_input)
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
