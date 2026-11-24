// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import com.ichi2.anki.CollectionManager.withCol
import com.ichi2.testutils.JvmTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EmptyCardsReportTest : JvmTest() {
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
