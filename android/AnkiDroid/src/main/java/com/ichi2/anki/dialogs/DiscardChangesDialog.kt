/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.R
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
        positiveButtonText: String = context.getString(R.string.discard),
        negativeButtonText: String = CollectionManager.TR.addingKeepEditing(),
        neutralButtonText: String? = null,
        message: String = CollectionManager.TR.cardTemplatesDiscardChanges(),
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
