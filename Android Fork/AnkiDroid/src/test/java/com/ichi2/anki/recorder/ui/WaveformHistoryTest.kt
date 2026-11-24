// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WaveformHistoryTest {
    @Test
    fun `keeps the latest levels once full`() {
        val history = WaveformHistory(capacity = 3)

        listOf(0, 32767, 0, 32767).forEach(history::add)

        assertEquals(3, history.size, "size")
        assertEquals(listOf(1f, 0f, 1f), (0 until 3).map { history.levelAt(it, visibleSlots = 3) }, "all levels")
        assertEquals(listOf(0f, 1f), (0 until 2).map { history.levelAt(it, visibleSlots = 2) }, "the latest two")
    }

    @Test
    fun `a failed reading and slots past the history have no level`() {
        val history = WaveformHistory()

        history.add(null)

        assertTrue(history.levelAt(0, visibleSlots = 10).isNaN(), "a failed reading")
        assertTrue(history.levelAt(1, visibleSlots = 10).isNaN(), "past the history")
    }
}
