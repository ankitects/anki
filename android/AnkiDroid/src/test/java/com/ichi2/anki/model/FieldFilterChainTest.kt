/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.model

import androidx.annotation.CheckResult
import com.ichi2.anki.model.FieldFilters.ClozeFilter
import com.ichi2.anki.model.FieldFilters.ClozeOnlyFilter
import com.ichi2.anki.model.FieldFilters.FuriganaFilter
import com.ichi2.anki.model.FieldFilters.HintFilter
import com.ichi2.anki.model.FieldFilters.KanaFilter
import com.ichi2.anki.model.FieldFilters.KanjiFilter
import com.ichi2.anki.model.FieldFilters.TextFilter
import com.ichi2.anki.model.FieldFilters.TextToSpeechFilter
import com.ichi2.anki.model.FieldFilters.TextToSpeechFilter.TextToSpeechOptions
import com.ichi2.anki.model.FieldFilters.TypeTheAnswerFilter
import com.ichi2.anki.model.FieldFilters.TypeTheAnswerNonCombiningFilter
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Tests [FieldFilterChain]
 */
class FieldFilterChainTest {
    @Test
    fun `render - empty chain`() {
        val chain = standardChain()

        assertEquals("{{$FIELD_NAME}}", chain.render())
    }

    @Test
    fun `render - single chain`() {
        val chain = standardChain().add(TextFilter)

        assertEquals("{{text:$FIELD_NAME}}", chain.render())
    }

    @Test
    fun `render - multi chain`() {
        val chain = standardChain().add(TextFilter).add(KanaFilter)

        assertEquals("{{kana:text:$FIELD_NAME}}", chain.render())
    }

    @Test
    fun `render - 'tts' with options`() {
        val chain =
            standardChain().add(
                TextToSpeechFilter(
                    options =
                        TextToSpeechOptions(
                            language = "ja_JP",
                            voices = listOf("Apple_Name", "Microsoft_Name"),
                            speed = 0.8f,
                        ),
                ),
            )

        assertEquals("{{tts lang=ja_JP voices=Apple_Name,Microsoft_Name speed=0.8:$FIELD_NAME}}", chain.render())
    }

    @Test
    fun `render - string representation`() {
        val map =
            mapOf(
                TextFilter to "text",
                ClozeFilter to "cloze",
                ClozeOnlyFilter to "cloze-only",
                HintFilter to "hint",
                TypeTheAnswerFilter to "type",
                TypeTheAnswerNonCombiningFilter to "type:nc",
                invalidTextToSpeechFilter() to "tts",
                FuriganaFilter to "furigana",
                KanaFilter to "kana",
                KanjiFilter to "kanji",
            )

        assertEquals(map.size, FieldFilters.ALL.size, message = "all filters handled")

        for ((filter, expectedName) in map) {
            assertEquals(expectedName, filter.name)
        }
    }

    @Test
    fun `tryAdd - the same filter cannot be added twice`() {
        val chain = standardChain().add(HintFilter)

        assertNull(chain.tryAdd(HintFilter), message = "duplicate filters cannot be added")
    }

    @Test
    fun `tryAdd - 'text' cannot be added after HTML producing fields`() {
        val htmlProducingFields =
            listOf(
                HintFilter,
                FuriganaFilter,
            )

        for (filter in htmlProducingFields) {
            val chain = standardChain().add(filter)
            assertNull(chain.tryAdd(TextFilter), message = filter.name)
        }
    }

    @Test
    fun `tryAdd - 'cloze' cannot be added to an empty non-cloze note type card template`() {
        assertNull(standardChain().tryAdd(ClozeFilter), message = "cloze")
        assertNull(standardChain().tryAdd(ClozeOnlyFilter), message = "cloze-only")
    }

    @Test
    fun `tryAdd - 'cloze' can be added to a cloze note type card template`() {
        assertNotNull(clozeChain().tryAdd(ClozeFilter), message = "cloze")
        assertNotNull(clozeChain().tryAdd(ClozeOnlyFilter), message = "cloze-only")
    }

    @Test
    fun `tryAdd - 'hint' can be applied`() {
        assertNotNull(standardChain().tryAdd(HintFilter))
    }

    @Test
    fun `tryAdd - 'hint' blocks 'text' filter`() {
        val chain = standardChain().add(HintFilter)

        assertNull(chain.tryAdd(TextFilter), message = "'text' cannot be added after 'hint'")
    }

    @Test
    fun `tryAdd - 'type' - no more filters can be added`() {
        val chain = standardChain().add(TypeTheAnswerFilter)

        for (filter in FieldFilters.ALL) {
            assertNull(chain.tryAdd(filter, allowInvalid = true), message = filter.name)
        }
    }

    @Test
    fun `tryAdd - 'type-nc' - no more filters can be added`() {
        val chain = standardChain().add(TypeTheAnswerNonCombiningFilter)

        for (filter in FieldFilters.ALL) {
            assertNull(chain.tryAdd(filter, allowInvalid = true), message = filter.name)
        }
    }

    @Test
    fun `tryAdd - 'tts' - can be valid`() {
        val tts = textToSpeechFilter()
        assertEquals(true, tts.isValid, "isValid")
    }

    @Test
    fun `tryAdd - 'tts' - can invalidate chain`() {
        val invalidTts = invalidTextToSpeechFilter()
        assertEquals(false, invalidTts.isValid, "!isValid")

        val chain = standardChain()
        assertEquals(true, chain.isValid, "chain is initially valid")

        assertNull(chain.tryAdd(invalidTts, allowInvalid = false), "allowInvalid blocks a bad TTS filter")

        val invalidChain = chain.add(invalidTts, allowInvalid = true)
        assertEquals(false, invalidChain.isValid, "chain is invalid if allowInvalid is set")

        // it's not possible totest that 'invalid' status propagates as TTS is terminal
    }

    @Test
    fun `tryAdd - 'tts' - no more filters can be added`() {
        val chain = standardChain().add(textToSpeechFilter(), allowInvalid = true)

        for (filter in FieldFilters.ALL) {
            assertNull(chain.tryAdd(filter, allowInvalid = true))
        }
    }

    @Test
    fun `tryAdd - 'kanji' - other pronunciation filters can't be added`() {
        val chain = standardChain().add(KanjiFilter)

        for (filter in pronunciationFilters) {
            assertNull(chain.tryAdd(filter))
        }
    }

    @Test
    fun `tryAdd - 'kanji' - 'text' can be added`() {
        val chain = standardChain().add(KanjiFilter)

        assertNotNull(chain.tryAdd(TextFilter))
    }

    @Test
    fun `tryAdd - 'kana' - other pronunciation filters can't be added`() {
        val chain = standardChain().add(KanaFilter)

        for (filter in pronunciationFilters) {
            assertNull(chain.tryAdd(filter))
        }
    }

    @Test
    fun `tryAdd - 'kana' - 'text' can be added`() {
        val chain = standardChain().add(KanaFilter)

        assertNotNull(chain.tryAdd(TextFilter))
    }

    @Test
    fun `tryAdd - 'furigana' - other pronunciation filters can't be added`() {
        val chain = standardChain().add(FuriganaFilter)

        for (filter in pronunciationFilters) {
            assertNull(chain.tryAdd(filter))
        }
    }

    @Test
    fun `tryAdd - 'furigana' - 'text' can't be added`() {
        val chain = standardChain().add(FuriganaFilter)

        assertNull(chain.tryAdd(TextFilter))
    }

    @CheckResult
    fun standardChain() = FieldFilterChain.build(FieldName(FIELD_NAME), false)

    @CheckResult
    fun clozeChain() = FieldFilterChain.build(FieldName(FIELD_NAME), true)

    @CheckResult
    fun invalidTextToSpeechFilter() = TextToSpeechFilter()

    @CheckResult
    fun textToSpeechFilter() = TextToSpeechFilter(TextToSpeechOptions(language = "en_GB"))

    companion object {
        const val FIELD_NAME = "Front"

        val pronunciationFilters =
            listOf(
                KanjiFilter,
                KanaFilter,
                FuriganaFilter,
            )
    }
}

/** Adds [filter] to the chain, or throws. */
fun FieldFilterChain.add(
    filter: FieldFilter,
    allowInvalid: Boolean = false,
): FieldFilterChain =
    requireNotNull(this.tryAdd(filter, allowInvalid)) {
        "${filter.name} could not be added"
    }
