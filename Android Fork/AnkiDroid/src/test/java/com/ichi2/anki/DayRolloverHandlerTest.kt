// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.observability.ChangeManager
import com.ichi2.testutils.JvmTest
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.WidgetStatus
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.After
import org.junit.Test

class DayRolloverHandlerTest : JvmTest() {
    @After
    fun clearMocks() {
        unmockkAll()
    }

    /** Issue 22041: overlapping initial time events must not flip the reviewer back to the question. */
    @Test
    fun `overlapping initial time changes do not report a day rollover`() =
        runTest {
            val scheduler = col.sched
            mockkObject(scheduler, DayRolloverAlarm, WidgetStatus)
            every { scheduler.dayCutoff } returns 86400L
            val subscriber = mockk<ChangeManager.Subscriber>(relaxed = true)
            ChangeManager.subscribe(subscriber)
            every { WidgetStatus.updateInBackground(any()) } just runs
            every { DayRolloverAlarm.scheduleNext(any()) } answers {
                // Let the second event check the same cutoff before the first event finishes.
                runCurrent()
            }

            repeat(2) { launch { DayRolloverHandler.handleTimeChange() } }
            advanceUntilIdle()

            verify(exactly = 0) { subscriber.opExecuted(any(), any()) }
            verify(exactly = 1) { DayRolloverAlarm.scheduleNext(any()) }
        }
}
