// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.BaselineProfileEntry.ClassRule
import com.ichi2.anki.BaselineProfileEntry.Flag
import com.ichi2.anki.BaselineProfileEntry.MethodRule
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class BaselineProfileEntryTest {
    @Test
    fun `parses a method rule`() {
        val rule = BaselineProfileEntry.parse("SPLcom/ichi2/anki/DeckPicker;->onResume()V")

        assertIs<MethodRule>(rule)
        assertEquals(setOf(Flag.STARTUP, Flag.POST_STARTUP), rule.flags)
        assertEquals("Lcom/ichi2/anki/DeckPicker;", rule.classDescriptor)
        assertEquals("onResume()V", rule.methodSignature)
        assertEquals("com.ichi2.anki.DeckPicker", rule.className)
        assertEquals("onResume", rule.methodName)
        assertEquals("SPLcom/ichi2/anki/DeckPicker;->onResume()V", rule.toString())
    }

    @Test
    fun `parses a class rule`() {
        val rule = BaselineProfileEntry.parse("Lcom/ichi2/anki/DeckPicker;")

        assertIs<ClassRule>(rule)
        assertEquals("Lcom/ichi2/anki/DeckPicker;", rule.classDescriptor)
        assertEquals("com.ichi2.anki.DeckPicker", rule.className)
        assertEquals("Lcom/ichi2/anki/DeckPicker;", rule.toString())
    }

    @Test
    fun `parses an inner class`() {
        val rule = BaselineProfileEntry.parse("HLcom/ichi2/anki/DeckPicker\$Companion;->foo(I)Z")

        assertIs<MethodRule>(rule)
        assertEquals(setOf(Flag.HOT), rule.flags)
        assertEquals("com.ichi2.anki.DeckPicker\$Companion", rule.className)
        assertEquals("foo", rule.methodName)
    }

    @Test
    fun `parseProfile skips blank lines and comments`() {
        val lines =
            listOf(
                "# Baseline profile rules for AnkiDroid",
                "",
                "Lcom/ichi2/anki/DeckPicker;",
                "  ",
                "SPLcom/ichi2/anki/DeckPicker;->onResume()V",
            )

        val rules = BaselineProfileEntry.parseProfile(lines)

        assertEquals(
            listOf("Lcom/ichi2/anki/DeckPicker;", "SPLcom/ichi2/anki/DeckPicker;->onResume()V"),
            rules.map { it.toString() },
        )
    }

    @Test
    fun `parseProfile still rejects malformed rules`() {
        assertFailsWith<IllegalArgumentException> {
            BaselineProfileEntry.parseProfile(listOf("Lcom/ichi2/anki/DeckPicker;", "XLcom/ichi2/anki/DeckPicker;"))
        }
    }

    @Test
    fun `rejects malformed rules`() {
        val malformed =
            listOf(
                // empty
                "",
                // flags without a descriptor
                "SP",
                // descriptor not starting with L
                "com/ichi2/anki/DeckPicker;",
                // unknown flag
                "XLcom/ichi2/anki/DeckPicker;",
                // class rule with flags
                "SLcom/ichi2/anki/DeckPicker;",
                // method rule without flags
                "Lcom/ichi2/anki/DeckPicker;->onResume()V",
            )
        for (line in malformed) {
            assertFailsWith<IllegalArgumentException>("expected '$line' to be rejected") {
                BaselineProfileEntry.parse(line)
            }
        }
    }
}
