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
