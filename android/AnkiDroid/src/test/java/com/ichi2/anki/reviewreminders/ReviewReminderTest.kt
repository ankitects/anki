/*
 *  Copyright (c) 2025 Eric Li <ericli3690@gmail.com>
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

package com.ichi2.anki.reviewreminders

import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.settings.Prefs
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class ReviewReminderTest : RobolectricTest() {
    companion object {
        /**
         * A mock time for a consistent date and a specific time: noon in the local time zone (not UTC! this is required because
         * the subject-under-test calculates times based on the local time zone, hence the convoluted construction of this mock).
         */
        private val mockTime =
            MockTime(
                initTime =
                    MockTime(2024, 0, 1, 0, 0, 0, 0, 0)
                        .calendar()
                        .apply {
                            set(Calendar.HOUR_OF_DAY, 12)
                            set(Calendar.MINUTE, 0)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis,
                step = 1000,
            )
    }

    @Before
    override fun setUp() {
        super.setUp()
        Prefs.reviewReminderNextFreeId = 0
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        Prefs.reviewReminderNextFreeId = 0
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `getAndIncrementNextFreeReminderId should increment IDs correctly`() {
        for (i in 0..10) {
            val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(hour = i, minute = i))
            assertThat(reminder.id, equalTo(ReviewReminderId(i)))
        }
    }

    @Test
    fun `latestNotifTime is set at creation`() {
        val timeBeforeCreation = TimeManager.time.calendar().timeInMillis
        val reviewReminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(hour = 1, minute = 0))
        val timeAfterCreation = TimeManager.time.calendar().timeInMillis

        assertThat(reviewReminder.latestNotifTime in timeBeforeCreation..timeAfterCreation, equalTo(true))
    }

    @Test
    fun `updateLatestNotifTime works correctly`() {
        val reviewReminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(hour = 1, minute = 0))

        val timeBeforeUpdate = TimeManager.time.calendar().timeInMillis
        reviewReminder.updateLatestNotifTime()
        val timeAfterUpdate = TimeManager.time.calendar().timeInMillis

        assertThat(reviewReminder.latestNotifTime in timeBeforeUpdate..timeAfterUpdate, equalTo(true))
    }

    @Test
    fun `notification should immediately fire if there was no scheduled firing`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 6.hours.inWholeMilliseconds.toInt(),
            lastFiringOffsetFromWindowStartMs = (-1).minutes.inWholeMilliseconds.toInt(),
            shouldImmediatelyFire = true,
        )
    }

    @Test
    fun `notification should not immediately fire if there was a scheduled firing`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 6.hours.inWholeMilliseconds.toInt(),
            lastFiringOffsetFromWindowStartMs = 1.minutes.inWholeMilliseconds.toInt(),
            shouldImmediatelyFire = false,
        )
    }

    @Test
    fun `notification should immediately fire if scheduled firing time is recent and there was no scheduled firing`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 2.minutes.inWholeMilliseconds.toInt(),
            lastFiringOffsetFromWindowStartMs = (-1).minutes.inWholeMilliseconds.toInt(),
            shouldImmediatelyFire = true,
        )
    }

    @Test
    fun `notification should not immediately fire if scheduled firing time is recent and there was a scheduled firing`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 1.minutes.inWholeMilliseconds.toInt(),
            lastFiringOffsetFromWindowStartMs = 0,
            shouldImmediatelyFire = false,
        )
    }

    @Test
    fun `notification should immediately fire if latest firing was a long time ago`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 0,
            lastFiringOffsetFromWindowStartMs = (-2).days.inWholeMilliseconds.toInt(),
            shouldImmediatelyFire = true,
        )
    }

    @Test
    fun `notification should immediately fire even if next window is approaching if there was no scheduled firing`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = (-1).minutes.inWholeMilliseconds.toInt(),
            lastFiringOffsetFromWindowStartMs = (-25).hours.inWholeMilliseconds.toInt(),
            shouldImmediatelyFire = true,
        )
    }

    @Test
    fun `notification should not immediately fire if scheduled firing was just late`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 23.hours.inWholeMilliseconds.toInt(),
            lastFiringOffsetFromWindowStartMs = 22.hours.inWholeMilliseconds.toInt(),
            shouldImmediatelyFire = false,
        )
    }

    @Test
    fun `notification should immediately fire if scheduled firing is now and latest firing was in the past`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 0,
            lastFiringOffsetFromWindowStartMs = (-1).minutes.inWholeMilliseconds.toInt(),
            shouldImmediatelyFire = true,
        )
    }

    @Test
    fun `notification should not immediately fire if scheduled firing is now and latest firing was just now`() {
        shouldImmediatelyFireTest(
            currentTimeOffsetFromWindowStartMs = 0,
            lastFiringOffsetFromWindowStartMs = 0,
            shouldImmediatelyFire = false,
        )
    }

    private fun shouldImmediatelyFireTest(
        currentTimeOffsetFromWindowStartMs: Int,
        lastFiringOffsetFromWindowStartMs: Int,
        shouldImmediatelyFire: Boolean,
    ) {
        val reviewReminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(hour = 1, minute = 0))

        val currentTime = mockTime.calendar().clone() as Calendar
        currentTime.apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.MILLISECOND, currentTimeOffsetFromWindowStartMs)
        }
        TimeManager.resetWith(MockTime(currentTime.timeInMillis, step = 1000))

        val lastFiring = mockTime.calendar().clone() as Calendar
        lastFiring.apply {
            set(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            add(Calendar.MILLISECOND, lastFiringOffsetFromWindowStartMs)
        }
        reviewReminder.latestNotifTime = lastFiring.timeInMillis

        assertThat(reviewReminder.shouldImmediatelyFire(), equalTo(shouldImmediatelyFire))

        TimeManager.reset()
    }
}
