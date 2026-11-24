// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.view.KeyEvent
import com.ichi2.anki.cardviewer.ViewerCommand
import com.ichi2.anki.cardviewer.ViewerCommand.entries
import com.ichi2.anki.reviewer.MappableBinding.Companion.toPreferenceString
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.hasItem
import org.hamcrest.Matchers.not
import org.junit.Test
import java.util.Arrays
import java.util.stream.Collectors
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MappableBindingTest {
    @Test
    fun equalityTest() {
        val allBindings = getAllBindings()

        assertThat(allBindings, hasItem(unicodeCharacter('@')))
        assertThat(allBindings, hasItem(keyCode(KeyEvent.KEYCODE_1)))
    }

    @Test
    fun inequalityTest() {
        val allBindings = getAllBindings()
        // pick an arbitrary key which is not mapped
        assertThat(allBindings, not(hasItem(unicodeCharacter('l'))))
        assertThat(allBindings, not(hasItem(keyCode(KeyEvent.KEYCODE_L))))
    }

    @Test
    fun `preference parsing drops invalid bindings and preserves valid bindings in order`() {
        val bindings = MappableBinding.fromPreferenceString("1/unknown|⌨Ctrl+66|⌨not-a-number|✅Ä|⍝SWIPE_UP|✅")

        assertEquals(listOf("⌨Ctrl+66", "✅Ä", "⍝SWIPE_UP"), bindings.map { it.toPreferenceString() })
    }

    @Test
    fun `preference parsing returns no bindings when all entries are invalid`() {
        assertTrue(MappableBinding.fromPreferenceString("1/unknown|⌨not-a-number|✅").isEmpty())
    }

    @Test
    fun `plus unicode bindings roundtrip through preferences`() {
        val bindings =
            listOf(
                MappableBinding(Binding.unicode(BindingUnicodeCharacter('+'), Binding.ModifierKeys.none())),
                MappableBinding(Binding.unicode(BindingUnicodeCharacter('+'), BindingTest.allModifierKeys())),
                MappableBinding(Binding.unicode(BindingUnicodeCharacter('Ä'), Binding.ModifierKeys.ctrl())),
            )

        assertEquals(bindings, MappableBinding.fromPreferenceString(bindings.toPreferenceString()))
    }

    @Test
    fun `plus unicode reviewer bindings preserve card sides through preferences`() {
        val bindings =
            CardSide.entries.map { side ->
                ReviewerBinding(Binding.unicode(BindingUnicodeCharacter('+'), BindingTest.allModifierKeys()), side)
            }
        val restored = ReviewerBinding.fromPreferenceString(bindings.toPreferenceString())

        assertEquals(bindings.map { it.binding }, restored.map { it.binding })
        assertEquals(bindings.map { it.side }, restored.map { it.side })
    }

    private fun getAllBindings() =
        Arrays
            .stream(entries.toTypedArray())
            .flatMap { x: ViewerCommand -> x.defaultValue.stream() }
            .collect(Collectors.toList())

    @Suppress("SameParameterValue")
    private fun keyCode(code: Int) = fromBinding(BindingTest.keyCode(code))

    @Suppress("SameParameterValue")
    private fun unicodeCharacter(char: Char) = fromBinding(BindingTest.unicodeCharacter(char))

    private fun fromBinding(binding: Binding): Any = ReviewerBinding(binding, CardSide.BOTH)
}
