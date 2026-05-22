/*
 *  Copyright (c) 2024 Arthur Milchior <arthur@milchior.fr>
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

package com.ichi2.anki.common.utils.android

import android.view.KeyEvent
import android.view.KeyEvent.ACTION_UP
import android.view.KeyEvent.KEYCODE_0
import android.view.KeyEvent.KEYCODE_5
import android.view.KeyEvent.KEYCODE_9
import android.view.KeyEvent.KEYCODE_BACK
import android.view.KeyEvent.KEYCODE_ENDCALL
import android.view.KeyEvent.KEYCODE_STAR
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Tests for [KeyEvent.digit]. */
@RunWith(AndroidJUnit4::class)
class KeyUtilsTest {
    @Test
    fun `digit returns the digit for 0-9 keys`() {
        assertEquals(0, KeyEvent(ACTION_UP, KEYCODE_0).digit)
        assertEquals(5, KeyEvent(ACTION_UP, KEYCODE_5).digit)
        assertEquals(9, KeyEvent(ACTION_UP, KEYCODE_9).digit)
    }

    @Test
    fun `digit returns null for non-digit printable keys`() {
        assertNull(KeyEvent(ACTION_UP, KEYCODE_STAR).digit)
    }

    @Test
    fun `digit returns null for non-printable keys`() {
        assertNull(KeyEvent(ACTION_UP, KEYCODE_ENDCALL).digit)
        assertNull(KeyEvent(ACTION_UP, KEYCODE_BACK).digit)
    }
}
