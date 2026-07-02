// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.android

import android.view.KeyEvent

/**
 * The decimal digit (0..9) represented by this [KeyEvent], or `null` if the event
 * does not represent a digit.
 */
val KeyEvent.digit: Int?
    get() {
        val unicodeChar = getUnicodeChar(0)
        return if (unicodeChar in '0'.code..'9'.code) unicodeChar - '0'.code else null
    }
