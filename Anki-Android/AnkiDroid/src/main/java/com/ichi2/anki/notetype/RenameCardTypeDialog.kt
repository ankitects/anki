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
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
import com.ichi2.utils.ValidationResult
import com.ichi2.utils.input
import com.ichi2.utils.negativeButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import com.ichi2.utils.title

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
                    title(R.string.rename_card_type)
                    positiveButton(R.string.rename) { }
                    negativeButton(R.string.dialog_cancel)
                    setView(R.layout.dialog_generic_text_input)
                }.input(
                    hint = CollectionManager.TR.actionsNewName().removeSuffix(":"),
                    displayKeyboard = true,
                    allowEmpty = false,
                    prefill = prefill,
                    validator = { text ->
                        val name = CardTypeName.fromString(text)
                        when {
                            currentName == name -> ValidationResult.REJECTED
                            !existingNames.contains(name) -> ValidationResult.VALID
                            else -> ValidationResult.error(context.getString(R.string.error_name_exists))
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
