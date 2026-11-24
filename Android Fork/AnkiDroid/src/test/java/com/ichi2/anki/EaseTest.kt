// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2024 Ben Wicks <benjaminlwicks@gmail.com>

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
