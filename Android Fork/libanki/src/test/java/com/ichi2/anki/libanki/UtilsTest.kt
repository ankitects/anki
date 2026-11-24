// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki.libanki

import org.junit.Assert.assertEquals
import org.junit.Test

class UtilsTest {
    @Test
    fun testSplit() {
        assertEquals(listOf("foo", "bar"), Utils.splitFields("foobar"))
        assertEquals(listOf("", "foo", "", "", ""), Utils.splitFields("foo"))
    }
}
