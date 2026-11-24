// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.utils.message
import com.ichi2.utils.negativeButton
import com.ichi2.utils.neutralButton
import com.ichi2.utils.positiveButton
import com.ichi2.utils.show
import timber.log.Timber

// TODO: Clean up this code
object DiscardChangesDialog {
    fun showDialog(
        context: Context,
        positiveButtonText: String = context.getString(CommonString.discard),
        negativeButtonText: String = with(context) { TR.sentenceCase.keepEditing },
        neutralButtonText: String? = null,
        message: String = TR.cardTemplatesDiscardChanges(),
        negativeMethod: () -> Unit = {},
        neutralMethod: (() -> Unit)? = null,
        positiveMethod: () -> Unit,
    ) = AlertDialog.Builder(context).show {
        Timber.i("showing 'discard changes' dialog")
        message(text = message)
        positiveButton(text = positiveButtonText) { positiveMethod() }
        negativeButton(text = negativeButtonText) { negativeMethod() }
        if (neutralButtonText != null && neutralMethod != null) {
            neutralButton(text = neutralButtonText) { neutralMethod() }
        }
    }
}
