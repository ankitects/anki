// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import android.view.KeyEvent
import org.mockito.ArgumentMatchers
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock

class KeyEventUtils {
    companion object {
        fun getVKey(): KeyEvent =
            mock {
                on { keyCode } doReturn KeyEvent.KEYCODE_V
                on { unicodeChar } doReturn 'v'.code
                on { getUnicodeChar(ArgumentMatchers.anyInt()) } doReturn 'v'.code
            }

        fun getInvalid(): KeyEvent =
            mock {
                on { keyCode } doReturn 0
                on { unicodeChar } doReturn 0
                on { getUnicodeChar(ArgumentMatchers.anyInt()) } doReturn 0
            }

        fun leftShift(): KeyEvent =
            mock {
                on { keyCode } doReturn KeyEvent.KEYCODE_SHIFT_LEFT
                on { unicodeChar } doReturn 0
                on { getUnicodeChar(ArgumentMatchers.anyInt()) } doReturn 0
            }
    }
}
