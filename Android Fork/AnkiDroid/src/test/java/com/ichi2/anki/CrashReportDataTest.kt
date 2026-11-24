// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CrashReportData.Companion.isDeckNotFoundInLimitsMapException
import com.ichi2.anki.CrashReportData.HelpAction
import com.ichi2.anki.libanki.Note
import com.ichi2.testutils.JvmTest
import com.ichi2.testutils.ext.triggerDeckNotFoundInLimitsMap
import net.ankiweb.rsdroid.BackendException
import net.ankiweb.rsdroid.exceptions.BackendNotFoundException
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CrashReportDataTest : JvmTest() {
    /** #15195: corrupt deck hierarchy raises 'deck not found in limits map' */
    @Test
    fun `deck not found in limits map regression test`() {
        triggerDeckNotFoundInLimitsMap()

        val ex = assertFailsWith<Exception> { col.sched.counts() }
        assertTrue(ex.isDeckNotFoundInLimitsMapException())
    }

    @Test
    fun `missing notetype offers a database check`() {
        val exception = assertFailsWith<BackendNotFoundException> { Note.fromNotetypeId(col, -1) }

        assertEquals(HelpAction.OpenCheckDatabase, CrashReportData(exception, "test", true).helpAction)
    }

    @Test
    fun `missing notetype is not reported as a crash`() {
        val exception = assertFailsWith<BackendNotFoundException> { Note.fromNotetypeId(col, -1) }

        assertFalse(CrashReportData(exception, "test", true).shouldReportException())
    }

    @Test
    fun `other backend errors are still reported`() {
        assertTrue(CrashReportData(BackendException("unexpected error"), "test", true).shouldReportException())
    }
}
