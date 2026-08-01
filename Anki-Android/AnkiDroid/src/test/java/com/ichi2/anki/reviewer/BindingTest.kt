/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.reviewer

import android.view.KeyEvent
import com.ichi2.anki.cardviewer.Gesture
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
        testModifierKeys("shift", KeyEvent::isShiftPressed, Binding.ModifierKeys::shiftMatches)
        testModifierKeys("ctrl", KeyEvent::isCtrlPressed) { k, ctrlPressed -> k.ctrl == ctrlPressed }
        testModifierKeys("alt", KeyEvent::isAltPressed) { k, altPressed -> k.alt == altPressed }
    }

    @Test
    fun unicodeKeyIsLoaded() {
        val binding = unicodeCharacter('a')

        assertThat(binding.unicodeCharacter, equalTo('a'))
    }

    @Test
    fun keycodeIsLoaded() {
        val binding = keyCode(KeyEvent.KEYCODE_A)

        assertThat(binding.keycode, equalTo(KeyEvent.KEYCODE_A))
    }

    @Test
    fun testUnicodeToString() {
        assertEquals(UNICODE_PREFIX + "Ä", Binding.unicode('Ä').toString())
        assertEquals(UNICODE_PREFIX + "Ctrl+Ä", Binding.unicode(Binding.ModifierKeys.ctrl(), 'Ä').toString())
        assertEquals(UNICODE_PREFIX + "Shift+Ä", Binding.unicode(Binding.ModifierKeys.shift(), 'Ä').toString())
        assertEquals(UNICODE_PREFIX + "Alt+Ä", Binding.unicode(Binding.ModifierKeys.alt(), 'Ä').toString())
        assertEquals(UNICODE_PREFIX + "Ctrl+Alt+Shift+Ä", Binding.unicode(allModifierKeys(), 'Ä').toString())
    }

    @Test
    fun testGestureToString() {
        assertEquals(GESTURE_PREFIX + "TAP_TOP", Binding.gesture(Gesture.TAP_TOP).toString())
    }

    @Test
    fun testUnknownToString() {
        // This seems sensible - serialising an unknown will mean that nothing is saved.
        assertThat(Binding.unknown().toString(), equalTo(""))
    }

    @Test
    fun testModifierKeysEquality() {
        val one = Binding.AppDefinedModifierKeys.allowShift()
        val two = Binding.ModifierKeys(shift = true, ctrl = false, alt = false)

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

        fun keyCode(keyCode: Int): Binding.KeyCode {
            val mock =
                mock<KeyEvent> {
                    on { getKeyCode() } doReturn keyCode
                }

            return Binding.possibleKeyBindings(mock).filterIsInstance<Binding.KeyCode>().first()
        }
    }
}
