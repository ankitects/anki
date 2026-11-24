// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.google.android.material.textfield.TextInputEditText

/** The Material answer field, including support for `{{nosuggest:type:}}`. */
class TypeAnswerEditText(
    context: Context,
    attrs: AttributeSet?,
) : TextInputEditText(context, attrs) {
    /**
     * Request raw keyboard input without disabling this view's cursor or input connection.
     *
     * Call [InputMethodManager.restartInput] after changing this flag if the keyboard is
     * already connected.
     *
     * @see applyNoSuggest
     */
    var noSuggest: Boolean = false

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? =
        super.onCreateInputConnection(outAttrs).applyNoSuggest(outAttrs, noSuggest)
}
