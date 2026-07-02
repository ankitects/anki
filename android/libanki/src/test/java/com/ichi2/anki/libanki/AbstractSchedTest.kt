/*
 Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>

 This program is free software; you can redistribute it and/or modify it under
 the terms of the GNU General Public License as published by the Free Software
 Foundation; either version 3 of the License, or (at your option) any later
 version.

 This program is distributed in the hope that it will be useful, but WITHOUT ANY
 WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 PARTICULAR PURPOSE. See the GNU General Public License for more details.

 You should have received a copy of the GNU General Public License along with
 this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.libanki

import anki.scheduler.CardAnswer.Rating
import com.ichi2.anki.libanki.sched.Counts
import com.ichi2.anki.libanki.testutils.InMemoryAnkiTest
import com.ichi2.anki.libanki.testutils.ext.addNote
import com.ichi2.anki.libanki.testutils.ext.newNote
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.notNullValue
import org.json.JSONArray
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class AbstractSchedTest : InMemoryAnkiTest() {
    @Test
    fun ensureUndoCorrectCounts() {
        val sched = col.sched
        val dconf = col.decks.getConfig(1)!!
        assertThat(dconf, notNullValue())
        dconf.new.perDay = 10
        col.decks.save(dconf)
        for (i in 0..19) {
            val note = col.newNote()
            note.setField(0, "a")
            col.addNote(note)
        }
        assertThat(col.cardCount(), equalTo(20))
        assertThat(sched.newCount(), equalTo(10))
        val card = sched.card
        assertThat(sched.newCount(), equalTo(10))
        assertThat(sched.counts().new, equalTo(10))
        sched.answerCard(card!!, Rating.GOOD)
        sched.card
        col.undo()
        assertThat(sched.newCount(), equalTo(10))
    }

    @Test
    fun undoAndRedo() {
        val conf = col.decks.configDictForDeckId(1)
        conf.new.delays = JSONArray(doubleArrayOf(1.0, 3.0, 5.0, 10.0))
        col.decks.save(conf)
        col.config.set("collapseTime", 20 * 60)
        val sched = col.sched

        addBasicNote("foo", "bar")

        var card = sched.card
        assertNotNull(card)
        assertEquals(Counts(1, 0, 0), sched.counts())

        sched.answerCard(card, Rating.GOOD)

        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, 1, 0),
            sched.counts(),
        )

        sched.answerCard(card, Rating.GOOD)

        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, 1, 0),
            sched.counts(),
        )

        assertNotNull(card)

        assertEquals(
            Counts(0, 1, 0),
            sched.counts(),
        )

        card = sched.card!!
        sched.answerCard(card, Rating.GOOD)
        card = sched.card
        assertNotNull(card)
        assertEquals(
            Counts(0, 1, 0),
            sched.counts(),
        )
        assertNotNull(card)
    }
}
