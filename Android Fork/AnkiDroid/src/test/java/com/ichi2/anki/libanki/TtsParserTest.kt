// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import androidx.annotation.CheckResult
import com.ichi2.anki.TtsParser.getTextsToRead
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test

class TtsParserTest {
    @Test
    fun clozeIsReplacedWithBlank() {
        val content = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}.cloze {font-weight: bold;color: blue;}</style>This is a <span class=cloze>[...]</span>"""
        val actual = getTtsTagFrom(content)
        assertThat(actual.fieldText, equalTo("This is a blank"))
    }

    @Test
    fun clozeIsReplacedWithBlankInTTSTag() {
        val content = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}.cloze {font-weight: bold;color: blue;}</style><tts service="android">This is a <span class=cloze>[...]</span></tts>"""
        val actual = getTtsTagFrom(content)
        assertThat(actual.fieldText, equalTo("This is a blank"))
    }

    @Test
    fun providedExampleClozeReplaces() {
        val content = """<style>.card {
 font-family: arial;
 font-size: 20px;
 text-align: center;
 color: black;
 background-color: white;
}.cloze {font-weight: bold;color: blue;}</style>A few lizards are venomous, eg <span class=cloze>[...]</span>. They have grooved teeth and sublingual venom glands."""
        val actual = getTtsTagFrom(content)
        assertThat(actual.fieldText, equalTo("A few lizards are venomous, eg blank. They have grooved teeth and sublingual venom glands."))
    }

    @CheckResult
    private fun getTtsTagFrom(content: String): TTSTag = getTextsToRead(content, "blank").single()
}
