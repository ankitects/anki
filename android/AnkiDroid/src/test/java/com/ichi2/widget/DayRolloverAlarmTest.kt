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

package com.ichi2.widget

import android.app.AlarmManager
import android.content.Context
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.services.AlarmManagerService
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.greaterThan
import org.junit.After
import org.junit.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DayRolloverAlarmTest : RobolectricTest() {
    private val mockContext: Context = mockk(relaxed = true)
    private val alarmManager: AlarmManager = mockk(relaxed = true)

    init {
        every { mockContext.getSystemService<AlarmManager>() } returns alarmManager
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
    }

    @Test
    fun `doScheduleNext calls setWindow with dayCutoff`() =
        runTest {
            val expectedCutoffMs = col.sched.dayCutoff * 1000L

            DayRolloverAlarm.scheduleNextInternal(mockContext)

            verify {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    expectedCutoffMs,
                    AlarmManagerService.WINDOW_LENGTH_MS,
                    any(),
                )
            }

            assertThat("alarm is in the future", expectedCutoffMs, greaterThan(collectionTime.intTimeMS()))
        }

    @Test
    fun `doScheduleNext does not crash`() =
        runTest {
            every {
                alarmManager.setWindow(AlarmManager.RTC_WAKEUP, any(), any(), any<android.app.PendingIntent>())
            } throws SecurityException("too many alarms")

            assertDoesNotThrow { DayRolloverAlarm.scheduleNextInternal(mockContext) }
        }
}
