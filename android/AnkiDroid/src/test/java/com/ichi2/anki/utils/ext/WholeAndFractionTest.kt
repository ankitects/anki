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

package com.ichi2.anki.utils.ext

import org.junit.Test
import kotlin.test.assertEquals

/** Tests for wholeAndFraction in Double.kt */
class WholeAndFractionTest {
    @Test
    fun wholeAndFraction_zero() {
        val (whole, fraction) = (0.0).wholeAndFraction()
        assertEquals(0L, whole)
        assertEquals(0.0, fraction)
    }

    @Test
    fun wholeAndFraction_positive() {
        val (whole, fraction) = (1.5).wholeAndFraction()
        assertEquals(1L, whole)
        assertEquals(0.5, fraction)
    }

    @Test
    fun wholeAndFraction_negative() {
        val (whole, fraction) = (-1.5).wholeAndFraction()
        // -1 + (-0.5) = -1.5
        assertEquals(-1L, whole)
        assertEquals(-0.5, fraction)
    }
}
