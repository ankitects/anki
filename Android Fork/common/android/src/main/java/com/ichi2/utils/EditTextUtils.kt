// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import android.widget.EditText

/** Moves the cursor to the end of the [EditText] */
fun EditText.moveCursorToEnd() = setSelection(text?.length ?: 0)

/**
 * Parses the [text][EditText.text] as an [Int] and returns the result, or `null` if the
 * string is not a valid representation of an [Int].
 *
 * note: "1.0" returns `null`
 */
fun EditText.textAsIntOrNull() = this.text.toString().toIntOrNull()

/**
 * Replaces the text in the EditText with the provided string
 *
 * Cursor position is maintained, unless at the end, in which it stays at the end
 */
fun EditText.replaceText(value: String) {
    if (this.text.toString() == value) return
    val cursor = selectionStart
    val isAtEnd = cursor == this.text.length
    setText(value)
    if (isAtEnd) {
        setSelection(value.length)
    } else {
        setSelection(cursor.coerceAtMost(value.length))
    }
}
