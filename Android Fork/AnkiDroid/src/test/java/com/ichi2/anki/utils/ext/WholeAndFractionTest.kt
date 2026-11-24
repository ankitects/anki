// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>

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
