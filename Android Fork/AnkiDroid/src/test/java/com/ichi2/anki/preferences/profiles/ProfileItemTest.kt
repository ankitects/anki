// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import com.ichi2.anki.multiprofile.ProfileId
import org.junit.Test
import kotlin.test.assertEquals

class ProfileItemTest {
    private fun initialOf(name: String) = ProfileItem(id = ProfileId.DEFAULT, name = name).initial

    @Test
    fun `initial is the uppercased first letter`() {
        assertEquals("W", initialOf("work"))
    }

    @Test
    fun `initial of an empty name falls back to a placeholder`() {
        assertEquals("?", initialOf(""))
    }

    @Test
    fun `initial keeps an emoji whole`() {
        assertEquals("😀", initialOf("😀 Study"))
    }

    @Test
    fun `initial keeps a flag emoji whole`() {
        assertEquals("🇮🇳", initialOf("🇮🇳 India"))
    }

    @Test
    fun `initial keeps a combining accent attached to its base letter`() {
        assertEquals("E\u0301", initialOf("e\u0301cole"))
    }
}
