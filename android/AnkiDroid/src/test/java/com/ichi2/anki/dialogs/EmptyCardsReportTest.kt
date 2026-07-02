/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.dialogs

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.anki.RobolectricTest
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class EmptyCardsReportTest : RobolectricTest() {
    @Test
    fun `backend report has expected html structure`() =
        runTest {
            addStandardNoteType("TestNotetype", arrayOf("f1", "f2"), "", "")
            val note1 = addNoteUsingNoteTypeName("TestNotetype", "f1text1", "f2text1")
            val note2 = addNoteUsingNoteTypeName("TestNotetype", "f1text2", "f2text2")
            val emptyCardsReport = withCol { getEmptyCards() }
            assertEquals(getExpectedEmptyCardsReport(note1.id, note2.id), emptyCardsReport.report)
        }

    private fun getExpectedEmptyCardsReport(
        nid1: Long,
        nid2: Long,
    ) =
        "<div><b>Empty cards for \u2068TestNotetype\u2069:</b></div><ol><li class=allempty>[anki:nid:$nid1] \u20681\u2069 of \u20681\u2069 cards empty (\u2068Card 1\u2069).</li><li class=allempty>[anki:nid:$nid2] \u20681\u2069 of \u20681\u2069 cards empty (\u2068Card 1\u2069).</li></ol>"

    @Test
    fun `backend report is empty when there are no empty cards`() =
        runTest {
            addNotes(12)
            val emptyCardsReport = withCol { getEmptyCards() }
            assertTrue(emptyCardsReport.report.isEmpty())
        }
}
