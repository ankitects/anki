// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CrashReportData.Companion.isDeckNotFoundInLimitsMapException
import com.ichi2.anki.libanki.QueueType
import com.ichi2.testutils.EmptyApplication
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
class CrashReportDataTest : RobolectricTest() {
    /** #15195: corrupt deck hierarchy raises 'deck not found in limits map' */
    @Test
    fun `deck not found in limits map regression test`() {
        addDeck("A::B::C").withNote(QueueType.New)
        val deckBDid = col.decks.idForName("A::B")!!
        val deckADid = col.decks.idForName("A")!!

        // Drop A::B -> A::B::C is under 'A', but has no entry in the limits map
        col.db.execute("delete from decks where id = ?", deckBDid)

        col.decks.select(deckADid)

        val ex = assertFailsWith<Exception> { col.sched.counts() }
        assertTrue(ex.isDeckNotFoundInLimitsMapException())
    }
}
