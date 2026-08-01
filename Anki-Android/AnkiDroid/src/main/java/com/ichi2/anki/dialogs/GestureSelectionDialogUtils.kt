/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
package com.ichi2.anki.dialogs

import android.content.Context
import androidx.core.view.isVisible
import com.ichi2.anki.cardviewer.GestureListener
import com.ichi2.ui.FixedTextView
import com.ichi2.ui.GesturePicker

/** Helper functions for a Dialog which wraps a [com.ichi2.ui.GesturePicker]  */
object GestureSelectionDialogUtils {
    fun getGesturePicker(context: Context): GesturePicker = GesturePicker(context)

    /** Supplies a callback which is called each time the user gesture selection changes
     *
     * This is **not** when the gesture is submitted */
    fun GesturePicker.onGestureChanged(listener: GestureListener) {
        setGestureChangedListener(listener)
    }
}

interface WarningDisplay {
    val warningTextView: FixedTextView

    fun setWarning(text: CharSequence) {
        warningTextView.isVisible = true
        warningTextView.text = text
    }

    fun clearWarning() {
        warningTextView.isVisible = false
        warningTextView.text = ""
    }
}
