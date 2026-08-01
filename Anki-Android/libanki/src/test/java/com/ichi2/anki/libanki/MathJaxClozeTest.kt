//noinspection MissingCopyrightHeader #8659

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.template.MathJax
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.testutils.clozeClass
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathJaxClozeTest : InMemoryAnkiTest() {
    @Test
    fun verifyMathJaxClozeCards() {
        val note =
            addClozeNote("{{c1::ok}} \\(2^2\\) {{c2::not ok}} \\(2^{{c3::2}}\\) \\(x^3\\) {{c4::blah}} {{c5::text with \\(x^2\\) jax}}")
        assertEquals(5, note.numberOfCards())

        val cards = note.cards()

        assertThat(cards[0].question(), containsString(clozeClass()))
        assertThat(cards[1].question(), containsString(clozeClass()))
        assertThat(cards[2].question(), not(containsString(clozeClass())))
        assertThat(cards[3].question(), containsString(clozeClass()))
        assertThat(cards[4].question(), containsString(clozeClass()))
    }

    @Test
    fun textContainsMathjax() {
        assertFalse(MathJax.textContainsMathjax("Hello world."))
        assertFalse(MathJax.textContainsMathjax(""))
        assertTrue(MathJax.textContainsMathjax("This is an inline! \\(1 \\div 2 =\\){{c1::\\(\\frac{1}{2}\\)}}"))
        assertTrue(MathJax.textContainsMathjax("This is two inlines! \\(1 \\div 2 =\\)\\(1 \\div 2 \\)"))
        assertTrue(MathJax.textContainsMathjax("This is an block equation! \\[1 \\div 2 = 1 \\div 2 \\]"))
        assertFalse(MathJax.textContainsMathjax("This has mismatched brackets! \\[1 \\div 2 = 1 \\div 2 \\)"))
        assertFalse(MathJax.textContainsMathjax("This has mismatched brackets too! \\(1 \\div 2 = 1 \\div 2 \\]"))
    }
}
