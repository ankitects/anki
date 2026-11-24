// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.notetype

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonString
import com.ichi2.utils.ValidationResult
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title
import com.ichi2.anki.common.android.R as CommonR

class RenameCardTypeDialog {
    companion object {
        /**
         * @param prefill The text to initially appear in the EditText
         * @param currentName The name of the card type to be renamed
         * @param existingNames Unsaved card type names from the currently edited note type,
         * used for validation.
         */
        fun showInstance(
            context: Context,
            prefill: String,
            currentName: CardTypeName,
            existingNames: List<CardTypeName>,
            block: (result: CardTypeName) -> Unit,
        ) {
            AlertDialog
                .Builder(context)
                .show {
                    title(CommonString.rename_card_type)
                    positiveButton(CommonString.rename) { }
                    negativeButton(CommonString.dialog_cancel)
                    setView(CommonR.layout.dialog_generic_text_input)
                }.input(
                    hint = CollectionManager.TR.actionsNewName().removeSuffix(":"),
                    displayKeyboard = true,
                    allowEmpty = false,
                    prefill = prefill,
                    validator = { text ->
                        val name = CardTypeName.fromString(text)
                        when {
                            name.value.replace("\"", "").isBlank() -> ValidationResult.REJECTED
                            currentName == name -> ValidationResult.REJECTED
                            !existingNames.contains(name) -> ValidationResult.VALID
                            else -> ValidationResult.error(context.getString(CommonString.error_name_exists))
                        }
                    },
                    callback = { dialog, result ->
                        block(CardTypeName.fromString(result.toString()))
                        dialog.dismiss()
                    },
                )
        }
    }
}
