// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.common.time

import org.junit.Assert.assertEquals
import org.junit.Test

class MockTimeTest {
    @Test
    fun dateTest() {
        val time = MockTime(2020, 7, 7, 7, 0, 0, 0, 0)
        assertEquals(1596783600000L, time.intTimeMS())
        assertEquals(1596783600000L, MockTime.timeStamp(2020, 7, 7, 7, 0, 0))
    }
}
