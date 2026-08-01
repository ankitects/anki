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
