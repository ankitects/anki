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
