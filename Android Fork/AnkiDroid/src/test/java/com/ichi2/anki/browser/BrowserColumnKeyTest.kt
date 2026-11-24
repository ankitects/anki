// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>

package com.ichi2.anki.browser

import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** Tests for [BrowserColumnKey] */
class BrowserColumnKeyTest : JvmTest() {
    @Test
    fun `browser column key test`() {
        val column = assertNotNull(col.getBrowserColumn(("noteFld")))

        val key = BrowserColumnKey.from(column)

        assertEquals("noteFld", key.value)
    }
}
