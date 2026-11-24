// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding.KeyCode
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import kotlin.reflect.KFunction1
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BindingTest {
    @Test
    fun modifierKeys_Are_Loaded() {
        // TODO: Stub a nonzero key code or Unicode character and assert that bindings is not empty.
        // The mock currently produces no bindings, so the assertion loop in testModifierKeys never runs.
        testModifierKeys("shift", KeyEvent::isShiftPressed, Binding.ModifierKeys::shiftMatches)
        testModifierKeys("ctrl", KeyEvent::isCtrlPressed) { k, ctrlPressed -> k.ctrl == ctrlPressed }
        testModifierKeys("alt", KeyEvent::isAltPressed) { k, altPressed -> k.alt == altPressed }
    }

    @Test
    fun unicodeKeyIsLoaded() {
        val binding = unicodeCharacter('a')

        assertThat(binding.unicodeCharacter, equalTo(BindingUnicodeCharacter('a')))
    }

    @Test
    fun `valid unicode input retains modifiers`() {
        val event =
            mock<KeyEvent> {
                on { keyCode } doReturn KeyEvent.KEYCODE_8
                on { getUnicodeChar(anyInt()) } doReturn '*'.code
                on { isShiftPressed } doReturn true
                on { isCtrlPressed } doReturn true
                on { isAltPressed } doReturn true
            }
        val binding = Binding.possibleKeyBindings(event).filterIsInstance<Binding.UnicodeCharacter>().single()

        assertEquals(BindingUnicodeCharacter('*'), binding.unicodeCharacter)
        assertTrue(binding.modifierKeys.shift)
        assertTrue(binding.modifierKeys.ctrl)
        assertTrue(binding.modifierKeys.alt)
    }

    @Test
    fun `forbidden unicode input retains the keycode binding and modifiers`() {
        val event =
            mock<KeyEvent> {
                on { keyCode } doReturn KeyEvent.KEYCODE_BACKSLASH
                on { getUnicodeChar(anyInt()) } doReturn MappableBinding.PREF_SEPARATOR.code
                on { isShiftPressed } doReturn true
                on { isCtrlPressed } doReturn true
                on { isAltPressed } doReturn true
            }

        assertEquals(
            listOf(KeyCode(KeyEvent.KEYCODE_BACKSLASH, allModifierKeys())),
            Binding.possibleKeyBindings(event),
        )
    }

    @Test
    fun keycodeIsLoaded() {
        val binding = keyCode(KeyEvent.KEYCODE_A)

        assertThat(binding.keycode, equalTo(KeyEvent.KEYCODE_A))
    }

    @Test
    fun testUnicodeToString() {
        val character = BindingUnicodeCharacter('Ä')
        assertEquals(UNICODE_PREFIX + "Ä", Binding.unicode(character).toString())
        assertEquals(UNICODE_PREFIX + "Ctrl+Ä", Binding.unicode(character, Binding.ModifierKeys.ctrl()).toString())
        assertEquals(UNICODE_PREFIX + "Shift+Ä", Binding.unicode(character, Binding.ModifierKeys.shift()).toString())
        assertEquals(UNICODE_PREFIX + "Alt+Ä", Binding.unicode(character, Binding.ModifierKeys.alt()).toString())
        assertEquals(UNICODE_PREFIX + "Ctrl+Alt+Shift+Ä", Binding.unicode(character, allModifierKeys()).toString())
    }

    @Test
    fun `plus unicode binding roundtrips with every modifier combination`() {
        val modifiers =
            listOf(
                Binding.ModifierKeys.none(),
                Binding.ModifierKeys.ctrl(),
                Binding.ModifierKeys.alt(),
                Binding.ModifierKeys.shift(),
                Binding.ModifierKeys(ctrl = true, alt = true),
                Binding.ModifierKeys(ctrl = true, shift = true),
                Binding.ModifierKeys(alt = true, shift = true),
                allModifierKeys(),
            )
        for (modifierKeys in modifiers) {
            val binding = Binding.unicode(BindingUnicodeCharacter('+'), modifierKeys)

            assertEquals(binding.toString(), binding, Binding.fromString(binding.toString()))
        }
    }

    @Test
    fun testGestureToString() {
        assertEquals(GESTURE_PREFIX + "TAP_TOP", Binding.gesture(Gesture.TAP_TOP).toString())
    }

    @Test
    fun testModifierKeysEquality() {
        val one = Binding.AppDefinedModifierKeys.allowShift()
        val two = Binding.ModifierKeys(shift = true)

        assertTrue(one.shiftMatches(true))
        assertTrue(one.shiftMatches(false))

        assertTrue(two.shiftMatches(true))
        assertFalse(two.shiftMatches(false))

        assertEquals(one, two)
        assertEquals(one.hashCode(), two.hashCode())
    }

    private fun testModifierKeys(
        name: String,
        event: KFunction1<KeyEvent, Boolean>,
        getValue: (Binding.ModifierKeys, Boolean) -> Boolean,
    ) {
        fun testModifierResult(
            event: KFunction1<KeyEvent, Boolean>,
            returnedFromMock: Boolean,
        ) {
            val mock =
                mock {
                    on(event) doReturn returnedFromMock
                }

            val bindings = Binding.possibleKeyBindings(mock)

            for (binding in bindings) {
                assertThat("Should match when '$name:$returnedFromMock': ", getValue(binding.modifierKeys, true), equalTo(returnedFromMock))
                assertThat(
                    "Should match when '$name:${!returnedFromMock}': ",
                    getValue(binding.modifierKeys, false),
                    equalTo(!returnedFromMock),
                )
            }
        }

        testModifierResult(event, true)
        testModifierResult(event, false)
    }

    companion object {
        const val GESTURE_PREFIX = '\u235D'
        const val KEY_PREFIX = '\u2328'
        const val UNICODE_PREFIX = '\u2705'
        const val JOYSTICK_PREFIX = '◯'

        fun allModifierKeys() = Binding.ModifierKeys(shift = true, ctrl = true, alt = true)

        fun unicodeCharacter(c: Char): Binding.UnicodeCharacter {
            val mock =
                mock<KeyEvent> {
                    on { getUnicodeChar(anyInt()) } doReturn c.code
                    on { unicodeChar } doReturn c.code
                }

            return Binding.possibleKeyBindings(mock).filterIsInstance<Binding.UnicodeCharacter>().first()
        }

        fun keyCode(keyCode: Int): KeyCode {
            val mock =
                mock<KeyEvent> {
                    on { getKeyCode() } doReturn keyCode
                }

            return Binding.possibleKeyBindings(mock).filterIsInstance<KeyCode>().first()
        }
    }
}
