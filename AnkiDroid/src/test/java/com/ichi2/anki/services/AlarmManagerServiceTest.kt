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

package com.ichi2.anki.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.core.os.BundleCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.services.NotificationService.Companion.EXTRA_REVIEW_REMINDER
import com.ichi2.anki.utils.ext.getParcelableCompat
import com.ichi2.testutils.ext.storeReminders
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class AlarmManagerServiceTest : RobolectricTest() {
    companion object {
        /**
         * Set the mock time to a consistent date and a specific time: noon in the local time zone (not UTC! this is required because
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
                step = 0,
            )
    }

    private lateinit var context: Context
    private lateinit var alarmManager: AlarmManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var reviewReminder: ReviewReminder

    @Before
    override fun setUp() {
        super.setUp()
        context = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        every { context.getSystemService<AlarmManager>() } returns alarmManager
        every { context.getSystemService<NotificationManager>() } returns notificationManager

        reviewReminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
        reviewReminder.latestNotifTime = mockTime.intTimeMS() // Ensure the reminder is ready to have its future instance scheduled

        TimeManager.resetWith(mockTime)
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
        TimeManager.reset()
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun `scheduleReviewReminderNotifications calls AlarmManager setWindow`() {
        val expectedSchedulingTime = mockTime.calendar().clone() as Calendar
        expectedSchedulingTime.apply {
            set(Calendar.HOUR_OF_DAY, 20)
        }
        AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                expectedSchedulingTime.timeInMillis,
                AlarmManagerService.WINDOW_LENGTH_MS,
                any(),
            )
        }
    }

    @Test
    fun `scheduleReviewReminderNotifications for past time calls AlarmManager setWindow with future time`() {
        val pastTimeReviewReminder =
            ReviewReminder.createReviewReminder(time = ReviewReminderTime(3, 0))
        val expectedSchedulingTime = mockTime.calendar().clone() as Calendar
        expectedSchedulingTime.apply {
            set(Calendar.HOUR_OF_DAY, 3)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        AlarmManagerService.scheduleReviewReminderNotification(context, pastTimeReviewReminder)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                expectedSchedulingTime.timeInMillis,
                AlarmManagerService.WINDOW_LENGTH_MS,
                any(),
            )
        }
    }

    @Test
    fun `unscheduleReviewReminderNotifications calls AlarmManager cancel`() {
        mockkStatic(PendingIntentCompat::class)
        val pendingIntent = mockk<PendingIntent>()
        every {
            PendingIntentCompat.getBroadcast(
                any(),
                any(),
                any(),
                PendingIntent.FLAG_UPDATE_CURRENT,
                any(),
            )
        } returns pendingIntent

        AlarmManagerService.unscheduleReviewReminderNotifications(context, reviewReminder)
        verify { alarmManager.cancel(pendingIntent) }
    }

    /**
     * A single test case for alarm scheduling testing.
     * @see scheduleAllNotificationsTest
     */
    private data class ScheduleAllNotificationsTestCase(
        val reminder: ReviewReminder,
        val shouldImmediatelySetAlarm: Boolean,
        val shouldImmediatelyFireNotif: Boolean,
        val latestNotifTime: EpochMilliseconds? = null,
    )

    /**
     * Helper function that tries scheduling multiple reminders and verifies the outcome for each.
     */
    private fun scheduleAllNotificationsTest(vararg testCases: ScheduleAllNotificationsTestCase) {
        require(testCases.map { it.reminder.time }.toSet().size == testCases.size) {
            "All test cases must have unique reminder times to facilitate alarm scheduling verification"
        }

        testCases.forEach { case ->
            if (case.latestNotifTime != null) {
                case.reminder.latestNotifTime = case.latestNotifTime
            }
        }
        val previousLatestNotifTimes = testCases.associate { it.reminder.id to it.reminder.latestNotifTime }

        ReviewRemindersDatabase.storeReminders(*testCases.map { it.reminder }.toTypedArray())
        val currentTimestamp = mockTime.calendar().clone() as Calendar

        AlarmManagerService.scheduleAllNotifications(context)

        testCases.forEach { case ->
            val expectedSchedulingTime = mockTime.calendar().clone() as Calendar
            expectedSchedulingTime.apply {
                set(Calendar.HOUR_OF_DAY, case.reminder.time.hour)
                set(Calendar.MINUTE, case.reminder.time.minute)
                set(Calendar.SECOND, 0)
                if (before(currentTimestamp)) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }

            val alarmSettingCallsExpected = if (case.shouldImmediatelySetAlarm) 1 else 0
            verify(exactly = alarmSettingCallsExpected) {
                alarmManager.setWindow(
                    AlarmManager.RTC_WAKEUP,
                    expectedSchedulingTime.timeInMillis,
                    10.minutes.inWholeMilliseconds,
                    any(),
                )
            }
        }

        val (testCasesWithFiring, testCasesWithoutFiring) = testCases.partition { it.shouldImmediatelyFireNotif }
        val expectedFired = testCasesWithFiring.map { it.reminder }.toSet()
        val expectedNotFired = testCasesWithoutFiring.map { it.reminder }.toSet()

        val capturedIntents = mutableListOf<Intent>()
        verify(exactly = expectedFired.size) {
            context.sendBroadcast(capture(capturedIntents))
        }
        val actuallyFired =
            capturedIntents
                .map { intent ->
                    intent.extras!!.getParcelableCompat<ReviewReminder>(EXTRA_REVIEW_REMINDER)!!
                }.toSet()
        val actuallyFiredIds = actuallyFired.map { it.id }.toSet()
        val notFired = testCases.map { it.reminder }.filterNot { it.id in actuallyFiredIds }.toSet()

        // The actually fired reminders have a modified latestNotifTime, so we can't directly compare the reminder objects
        assertThat((actuallyFired + notFired).map { it.id }.toSet(), equalTo(testCases.map { it.reminder.id }.toSet()))
        assertThat(expectedFired.map { it.id }.toSet(), equalTo(actuallyFired.map { it.id }.toSet()))

        // But we can compare unfired reminders directly since they should be unchanged
        assertThat(expectedNotFired, equalTo(notFired))

        // Validate latestNotifTime has changed for fired reminders
        val expectedFiredCreationTimes = expectedFired.associate { it.id to previousLatestNotifTimes[it.id]!! }
        val notificationTimes = actuallyFired.associate { it.id to it.latestNotifTime }
        assertThat(expectedFiredCreationTimes.keys.toSet(), equalTo(notificationTimes.keys.toSet()))
        expectedFiredCreationTimes.forEach { (id, createdAt) ->
            val notifTime = notificationTimes[id]!!
            assertThat(notifTime > createdAt, equalTo(true))
        }

        // Validate stored reminders
        val stored = ReviewRemindersDatabase.getAllAppWideReminders() + ReviewRemindersDatabase.getAllDeckSpecificReminders()
        val (storedFired, storedNotFired) = stored.getRemindersList().partition { it.id in actuallyFired.map { r -> r.id } }
        assertThat(storedFired.toSet(), equalTo(actuallyFired))
        assertThat(storedNotFired.toSet(), equalTo(notFired))
    }

    @Test
    fun `scheduleAllNotifications schedules reminders for all enabled reminders in database`() =
        runTest {
            val did1 = addDeck("Deck1")
            val did2 = addDeck("Deck2")
            scheduleAllNotificationsTest(
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(9, 0),
                            scope = ReviewReminderScope.DeckSpecific(did1),
                        ),
                    shouldImmediatelySetAlarm = true,
                    shouldImmediatelyFireNotif = false,
                ),
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(10, 0),
                            scope = ReviewReminderScope.DeckSpecific(did2),
                        ),
                    shouldImmediatelySetAlarm = true,
                    shouldImmediatelyFireNotif = false,
                ),
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(11, 0),
                        ),
                    shouldImmediatelySetAlarm = true,
                    shouldImmediatelyFireNotif = false,
                ),
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(12, 0),
                            enabled = false,
                        ),
                    shouldImmediatelySetAlarm = false,
                    shouldImmediatelyFireNotif = false,
                ),
            )
        }

    @Test
    fun `scheduleAllNotifications immediately fires notification for reminders which missed scheduled firings`() =
        runTest {
            val did1 = addDeck("Deck1")
            scheduleAllNotificationsTest(
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(11, 58), // Last scheduled at 2 minutes earlier
                            scope = ReviewReminderScope.DeckSpecific(did1),
                        ),
                    shouldImmediatelySetAlarm = false,
                    shouldImmediatelyFireNotif = true, // Should fire immediately, because...
                    latestNotifTime = mockTime.intTimeMS() - 10.minutes.inWholeMilliseconds, // ...latest firing was 10 minutes ago
                ),
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(11, 59), // Last scheduled at 1 minute earlier
                            scope = ReviewReminderScope.Global,
                        ),
                    shouldImmediatelySetAlarm = true,
                    shouldImmediatelyFireNotif = false, // Should not fire immediately, because...
                    latestNotifTime = mockTime.intTimeMS(), // ...a notification just fired
                ),
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(12, 1), // Last scheduled almost a full day ago
                            scope = ReviewReminderScope.Global,
                        ),
                    shouldImmediatelySetAlarm = true,
                    shouldImmediatelyFireNotif = false, // Should not fire immediately, because...
                    latestNotifTime = mockTime.intTimeMS() - 10.minutes.inWholeMilliseconds, // ...latest firing was 10 minutes ago
                ),
                ScheduleAllNotificationsTestCase(
                    reminder =
                        ReviewReminder.createReviewReminder(
                            time = ReviewReminderTime(12, 2), // Last scheduled almost a full day ago
                            scope = ReviewReminderScope.DeckSpecific(did1),
                        ),
                    shouldImmediatelySetAlarm = false,
                    shouldImmediatelyFireNotif = true, // Should fire immediately, because...
                    latestNotifTime = mockTime.intTimeMS() - 2.days.inWholeMilliseconds, // ...latest firing was 2 days ago
                ),
            )
        }

    @Test
    fun `onReceive schedules snoozed notification and cancels clicked notification`() {
        val extras = mockk<Bundle>()
        every { extras.getInt(any()) } returns 5
        val intent = mockk<Intent>()
        every { intent.extras } returns extras
        mockkStatic(BundleCompat::class)
        every { BundleCompat.getParcelable(extras, any(), ReviewReminder::class.java) } returns reviewReminder

        AlarmManagerService().onReceive(context, intent)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                mockTime.intTimeMS() + 5.minutes.inWholeMilliseconds,
                AlarmManagerService.WINDOW_LENGTH_MS,
                any(),
            )
        }
        verify { notificationManager.cancel(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value) }
    }
}
