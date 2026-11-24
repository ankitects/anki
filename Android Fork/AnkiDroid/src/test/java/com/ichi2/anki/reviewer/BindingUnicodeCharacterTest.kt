// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import kotlin.test.assertNull

class BindingUnicodeCharacterTest {
    @Test
    fun `preference separator cannot be constructed`() {
        assertThrows(IllegalArgumentException::class.java) {
            BindingUnicodeCharacter(MappableBinding.PREF_SEPARATOR)
        }
    }

    @Test
    fun `preference separator is rejected without throwing`() {
        assertNull(BindingUnicodeCharacter.fromCharOrNull(MappableBinding.PREF_SEPARATOR))
    }

    @Test
    fun `supported characters retain their values`() {
        for (character in listOf('a', 'Ä', ' ', '*')) {
            assertEquals(character, BindingUnicodeCharacter(character).value)
            assertEquals(BindingUnicodeCharacter(character), BindingUnicodeCharacter.fromCharOrNull(character))
        }
    }
}
