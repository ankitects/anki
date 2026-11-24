// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.targetContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** Uses application startup to register the custom `nosuggest` field filter. */
@RunWith(AndroidJUnit4::class)
class TypeAnswerAndroidTest :
    JvmTest(),
    AndroidTest {
    @Test
    fun `HTML answer marks nosuggest fields and requests a Done key`() =
        runTest {
            val field = htmlAnswerField("nosuggest:type")

            assertEquals("true", field.attr("data-ankidroid-nosuggest"))
            assertEquals("done", field.attr("enterkeyhint"))
        }

    @Test
    fun `ordinary HTML answers keep their keyboard defaults`() =
        runTest {
            val field = htmlAnswerField("type")

            assertFalse(field.hasAttr("data-ankidroid-nosuggest"))
            assertFalse(field.hasAttr("enterkeyhint"))
        }

    private fun htmlAnswerField(filter: String): Element {
        val card = addBasicWithTypingNote("front", "back").firstCard()
        val noteType = card.noteType(col)
        noteType.templates[0].qfmt = "{{Front}}{{$filter:Back}}"
        col.notetypes.save(noteType)

        val typeAnswer = TypeAnswer(useInputTag = true, autoFocus = true)
        typeAnswer.updateInfo(col, card, targetContext.resources)
        val html = typeAnswer.filterQuestion(card.question(col))
        return assertNotNull(Jsoup.parseBodyFragment(html).selectFirst("input#typeans"))
    }
}
