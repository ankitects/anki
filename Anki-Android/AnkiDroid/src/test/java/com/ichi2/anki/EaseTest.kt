/*
 *  Copyright (c) 2024 Ben Wicks <benjaminlwicks@gmail.com>
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

package com.ichi2.anki

import org.junit.Assert.assertEquals
import org.junit.Test

class EaseTest {
    @Test
    @Suppress("DEPRECATION")
    fun `Ease enum values match api module`() {
        assertEquals(com.ichi2.anki.libanki.sched.Ease.AGAIN.value, com.ichi2.anki.api.Ease.EASE_1.value)
        assertEquals(com.ichi2.anki.libanki.sched.Ease.HARD.value, com.ichi2.anki.api.Ease.EASE_2.value)
        assertEquals(com.ichi2.anki.libanki.sched.Ease.GOOD.value, com.ichi2.anki.api.Ease.EASE_3.value)
        assertEquals(com.ichi2.anki.libanki.sched.Ease.EASY.value, com.ichi2.anki.api.Ease.EASE_4.value)
        assertEquals(com.ichi2.anki.libanki.sched.Ease.entries.size, com.ichi2.anki.api.Ease.entries.size)
    }
}
