/*
 *  Copyright (c) 2025 Arthur Milchior <arthur@milchior.fr>
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

import androidx.test.espresso.matcher.ViewMatchers.assertThat
import com.ichi2.anki.libanki.DeckConfig.New
import com.ichi2.anki.reviewer.AutomaticAnswerAction
import com.ichi2.anki.reviewer.AutomaticAnswerAction.Companion.answerAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.QuestionAction
import com.ichi2.anki.ui.windows.reviewer.autoadvance.QuestionAction.Companion.questionAction
import com.ichi2.testutils.isJsonHolderEqual
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DeckConfigTest {
    val dc = DeckConfig("{}")

    @Test
    fun testConf() {
        dc.conf = 4L
        assertEquals(4L, dc.conf)
    }

    @Test
    fun testId() {
        dc.id = 42L
        assertEquals(42L, dc.id)
    }

    @Test
    fun testWaitForAudio() {
        assertTrue(dc.waitForAudio)
        val dc = DeckConfig("""{"waitForAudio": false}""")
        assertFalse(dc.waitForAudio)
    }

    @Test
    fun testAnswerAction() {
        val dc = DeckConfig("""{"answerAction": ${AutomaticAnswerAction.ANSWER_AGAIN.configValue}}""")
        assertEquals(AutomaticAnswerAction.ANSWER_AGAIN, dc.answerAction)
        dc.removeAnswerAction()
        assertEquals(AutomaticAnswerAction.BURY_CARD, dc.answerAction)
        val dcError = DeckConfig("""{"answerAction": 42}""")
        assertEquals(AutomaticAnswerAction.BURY_CARD, dcError.answerAction)
    }

    @Test
    fun testStopTimeOnAnswer() {
        val dc = DeckConfig("""{"stopTimerOnAnswer": true}""")
        assertTrue(dc.stopTimerOnAnswer)
    }

    @Test
    fun testQuestionAction() {
        assertEquals(QuestionAction.SHOW_ANSWER, dc.questionAction)
        val dc = DeckConfig(""" {"questionAction": 1} """)
        assertEquals(QuestionAction.SHOW_REMINDER, dc.questionAction)
    }

    @Test
    fun testSecondsToShowQuestion() {
        assertEquals(0.0, dc.secondsToShowQuestion)
        dc.secondsToShowQuestion = 42.0
        assertEquals(42.0, dc.secondsToShowQuestion)
    }

    @Test
    fun testSecondsToShowAnswer() {
        assertEquals(0.0, dc.secondsToShowAnswer)
        dc.secondsToShowAnswer = 42.0
        assertEquals(42.0, dc.secondsToShowAnswer)
    }

    @Test
    fun testMaxTaken() {
        val dc = DeckConfig(""" {"maxTaken": 42} """)
        assertEquals(42, dc.maxTaken)
    }

    @Test
    fun testAutoPlay() {
        assertTrue(dc.autoplay)
        dc.autoplay = false
        assertFalse(dc.autoplay)
        dc.autoplay = true
        assertTrue(dc.autoplay)
    }

    @Test
    fun testReplayQ() {
        dc.replayq = false
        assertFalse(dc.replayq)
        dc.replayq = true
        assertTrue(dc.replayq)
    }

    @Test
    fun testTimer() {
        var dc = DeckConfig("""{"timer": true}""")
        assertTrue(dc.timer)
        dc = DeckConfig("""{"timer": 1}""")
        assertTrue(dc.timer)
        dc = DeckConfig("""{"timer": false}""")
        assertTrue(!dc.timer)
        dc = DeckConfig("""{"timer": 0}""")
        assertTrue(!dc.timer)
    }

    val n = New(JSONObject())

    @Test
    fun testPerDay() {
        n.perDay = 42
        assertEquals(42, n.perDay)
    }

    @Test
    fun testNewDelays() {
        val ar = JSONArray(longArrayOf(42, 1))
        n.delays = ar
        assertEquals(ar, n.delays)
    }

    @Test
    fun testBury() {
        n.bury = true
        assertTrue(n.bury)
        n.bury = false
        assertTrue(!n.bury)
    }

    @Test
    fun testNew() {
        val dc = DeckConfig("""{"new": {}}""")
        assertThat(dc.new, isJsonHolderEqual("{}"))
    }

    val lapse = DeckConfig.Lapse(JSONObject())

    @Test
    fun testLapse() {
        val dc = DeckConfig("""{"lapse": {}}""")
        assertThat(dc.lapse, isJsonHolderEqual("{}"))
    }

    @Test
    fun testLapseDelays() {
        val ar = JSONArray(longArrayOf(42, 1))
        lapse.delays = ar
        assertEquals(ar, lapse.delays)
    }

    @Test
    fun testLeechAction() {
        lapse.leechAction = 42
        assertEquals(42, lapse.leechAction)
    }

    @Test
    fun testMult() {
        lapse.mult = 42.0
        assertEquals(42.0, lapse.mult)
    }

    val rev = DeckConfig.Rev(JSONObject())

    @Test
    fun testRev() {
        val dc = DeckConfig("""{"rev": {}}""")
        assertThat(dc.rev, isJsonHolderEqual("{}"))
    }

    @Test
    fun testRevDelays() {
        val ar = JSONArray(longArrayOf(42, 1))
        rev.delays = ar
        assertEquals(ar, rev.delays)
    }

    @Test
    fun testHardFactor() {
        rev.hardFactor = 42
        assertEquals(42, rev.hardFactor)
    }
}
