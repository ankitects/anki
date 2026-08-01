/*
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

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import net.ankiweb.rsdroid.BackendFactory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class LanguageUtilRobolectricTest : RobolectricTest() {
    @Test
    @Config(qualifiers = "zn")
    fun `Language without region is set`() {
        assertEquals(BackendFactory.defaultLanguages, listOf("zn"))
    }

    @Test
    @Config(qualifiers = "zn-rTW")
    fun `Language with region is set`() {
        assertEquals(BackendFactory.defaultLanguages, listOf("zn-TW"))
    }
}
