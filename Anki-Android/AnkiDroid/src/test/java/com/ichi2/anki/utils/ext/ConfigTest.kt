/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/**
 * Tests for extensions to [com.ichi2.anki.libanki.Config]
 */
@RunWith(AndroidJUnit4::class)
class ConfigTest : RobolectricTest() {
    @Test
    fun `test non-diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("uber").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("uber").size)
    }

    @Test
    fun `test diacritic input`() {
        addBasicNote("uber")
        addBasicNote("über")
        addBasicNote("Über")

        assertEquals(1, col.findCards("über").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("über").size)
    }

    @Test
    fun `test Japanese input`() {
        addBasicNote("は")
        addBasicNote("ば")
        addBasicNote("ぱ")

        assertEquals(1, col.findCards("は").size)

        col.config.ignoreAccentsInSearch = true

        assertEquals(3, col.findCards("は").size)
    }
}
