/*
 *  Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>
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

package com.ichi2.anki.browser

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.JvmTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Tests for [BrowserColumnKey] */
@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class BrowserColumnKeyTest : JvmTest() {
    @Test
    fun `browser column key test`() {
        val column = assertNotNull(col.getBrowserColumn(("noteFld")))

        val key = BrowserColumnKey.from(column)

        assertEquals("noteFld", key.value)
    }
}
