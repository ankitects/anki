// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding
import com.ichi2.anki.reviewer.Binding.KeyCode
import com.ichi2.anki.reviewer.BindingTest
import com.ichi2.anki.reviewer.BindingUnicodeCharacter
import com.ichi2.anki.reviewer.CardSide
import com.ichi2.anki.reviewer.MappableBinding
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import com.ichi2.anki.reviewer.ReviewerBinding
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.test.assertTrue

class BindingPreferenceTest {
    @Test
    fun serialization_deserialization_returns_same_result() {
        val str = getSampleBindings().toPreferenceString()

        val again = ReviewerBinding.fromPreferenceString(str)

        assertEquals(str, again.toPreferenceString())
    }

    @Test
    fun test_serialisation_does_not_change() {
        // If this changes, we have introduced a breaking change in the serialization
        //
        val expected = "1/r✅a2|r✅ 1|r⍝DOUBLE_TAP2|r⌨122"

        assertEquals(expected, getSampleBindings().toPreferenceString())
    }

    @Test
    fun `unicode characters and modifiers survive preference round trip`() {
        val bindings =
            listOf(
                ReviewerBinding(Binding.UnicodeCharacter(BindingUnicodeCharacter('Ä'), BindingTest.allModifierKeys()), CardSide.QUESTION),
                ReviewerBinding(Binding.UnicodeCharacter(BindingUnicodeCharacter(' ')), CardSide.ANSWER),
                ReviewerBinding(Binding.UnicodeCharacter(BindingUnicodeCharacter('*')), CardSide.BOTH),
            )
        val expected = "1/r✅Ctrl+Alt+Shift+Ä0|r✅ 1|r✅*2"

        assertEquals(expected, bindings.toPreferenceString())
        assertEquals(bindings, ReviewerBinding.fromPreferenceString(expected))
    }

    @Test
    fun `reviewer preference parsing preserves valid bindings and card sides around invalid entries`() {
        val bindings = ReviewerBinding.fromPreferenceString("1/r⌨Ctrl+660|r⍝LONG_TAP2|r✅Ä1|r⌨not-a-number2|r⍝SWIPE_UP2")

        assertEquals(3, bindings.size)
        assertEquals("1/r⌨Ctrl+660|r✅Ä1|r⍝SWIPE_UP2", bindings.toPreferenceString())
    }

    @Test
    fun `reviewer preference parsing returns no bindings when all entries are invalid`() {
        assertTrue(ReviewerBinding.fromPreferenceString("1/r⍝LONG_TAP2|r⌨not-a-number2|r✅2").isEmpty())
    }

    private fun getSampleBindings(): List<MappableBinding> =
        listOf(
            ReviewerBinding(Binding.unicode(BindingUnicodeCharacter('a')), CardSide.BOTH),
            ReviewerBinding(Binding.unicode(BindingUnicodeCharacter(' ')), CardSide.ANSWER),
            ReviewerBinding(Binding.gesture(Gesture.DOUBLE_TAP), CardSide.BOTH),
            ReviewerBinding(KeyCode(12), CardSide.BOTH),
        )
}
