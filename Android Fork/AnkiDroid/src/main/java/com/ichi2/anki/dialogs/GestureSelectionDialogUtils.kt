// SPDX-License-Identifier: GPL-3.0-or-later

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
