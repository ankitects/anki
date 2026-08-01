// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.services

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.services.NotificationService.Companion.EXTRA_REVIEW_REMINDER_ID
import com.ichi2.anki.services.NotificationService.Companion.EXTRA_REVIEW_REMINDER_SCOPE
import com.ichi2.anki.utils.ext.getParcelableCompat
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import kotlin.time.Duration
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
        AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder, attemptImmediateNotification = true)
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
        AlarmManagerService.scheduleReviewReminderNotification(context, pastTimeReviewReminder, attemptImmediateNotification = false)
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
    fun `scheduleReviewReminderNotifications for current time calls AlarmManager setWindow with future time`() {
        val currentTimeReviewReminder =
            ReviewReminder.createReviewReminder(time = ReviewReminderTime(12, 0))
        val expectedSchedulingTime = mockTime.calendar().clone() as Calendar
        expectedSchedulingTime.apply {
            set(Calendar.HOUR_OF_DAY, 12)
            add(Calendar.DAY_OF_YEAR, 1)
        }
        AlarmManagerService.scheduleReviewReminderNotification(context, currentTimeReviewReminder, attemptImmediateNotification = true)
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
    fun `scheduleReviewReminderNotifications attempts immediate notification when flag is true`() {
        AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder, attemptImmediateNotification = true)

        val slot = slot<Intent>()
        verify(exactly = 1) {
            context.sendBroadcast(capture(slot))
        }
        val firedReminderId = slot.captured.extras!!.getParcelableCompat<ReviewReminderId>(EXTRA_REVIEW_REMINDER_ID)!!
        val firedReminderScope = slot.captured.extras!!.getParcelableCompat<ReviewReminderScope>(EXTRA_REVIEW_REMINDER_SCOPE)!!
        assertThat(firedReminderId, equalTo(reviewReminder.id))
        assertThat(firedReminderScope, equalTo(reviewReminder.scope))
    }

    @Test
    fun `scheduleReviewReminderNotifications does not attempt immediate notification when flag is false`() {
        AlarmManagerService.scheduleReviewReminderNotification(context, reviewReminder, attemptImmediateNotification = false)

        verify(exactly = 0) {
            context.sendBroadcast(any())
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

    @Test
    fun `scheduleAllNotifications schedules and fires reminders for all enabled reminders in database`() =
        runTest {
            val did1 = addDeck("Deck1")
            val did2 = addDeck("Deck2")
            val reviewReminders =
                listOf(
                    ReviewReminder.createReviewReminder(
                        time = ReviewReminderTime(9, 0),
                        scope = ReviewReminderScope.DeckSpecific(did1),
                    ),
                    ReviewReminder.createReviewReminder(
                        time = ReviewReminderTime(10, 0),
                        scope = ReviewReminderScope.DeckSpecific(did2),
                    ),
                    ReviewReminder.createReviewReminder(
                        time = ReviewReminderTime(11, 0),
                    ),
                    ReviewReminder.createReviewReminder(
                        time = ReviewReminderTime(12, 0),
                        enabled = false,
                    ),
                )
            reviewReminders.forEach { ReviewRemindersDatabase.insertReminder(it) }

            AlarmManagerService.scheduleAllNotifications(context)

            reviewReminders.forEach { reminder ->
                val expectedSchedulingTime = mockTime.calendar().clone() as Calendar
                expectedSchedulingTime.apply {
                    set(Calendar.HOUR_OF_DAY, reminder.time.hour)
                    set(Calendar.MINUTE, reminder.time.minute)
                    set(Calendar.SECOND, 0)
                    if (before(mockTime.calendar())) {
                        add(Calendar.DAY_OF_YEAR, 1)
                    }
                }

                val alarmSettingCallsExpected = if (reminder.enabled) 1 else 0
                verify(exactly = alarmSettingCallsExpected) {
                    alarmManager.setWindow(
                        AlarmManager.RTC_WAKEUP,
                        expectedSchedulingTime.timeInMillis,
                        AlarmManagerService.WINDOW_LENGTH_MS,
                        any(),
                    )
                }
            }

            val expectedFiredReminderIds = reviewReminders.filter { it.enabled }.map { it.id }.toSet()
            val capturedIntents = mutableListOf<Intent>()
            verify(exactly = expectedFiredReminderIds.size) {
                context.sendBroadcast(capture(capturedIntents))
            }
            val firedReminderIds =
                capturedIntents
                    .map { intent ->
                        intent.extras!!.getParcelableCompat<ReviewReminderId>(EXTRA_REVIEW_REMINDER_ID)!!
                    }.toSet()
            assertThat(firedReminderIds, equalTo(expectedFiredReminderIds))
        }

    @Test
    fun `triggering schedules snoozed notification and cancels clicked notification`() =
        runTest {
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            attemptSnooze(reviewReminder, 5.minutes)

            verifyNotifSnoozed(5.minutes)
            verifyPastNotifCleared(reviewReminder)
        }

    @Test
    fun `triggering only clears past notif if review reminder is not in database`() =
        runTest {
            attemptSnooze(reviewReminder, 5.minutes)

            verifyNoNotifSnoozed()
            verifyPastNotifCleared(reviewReminder)
        }

    private suspend fun attemptSnooze(
        reviewReminder: ReviewReminder,
        delay: Duration,
    ) {
        AlarmManagerService.handleSnoozeReviewReminder(
            context,
            reviewReminder.id,
            reviewReminder.scope,
            snoozeIntervalInMinutes = delay.inWholeMinutes.toInt(),
        )
    }

    private fun verifyNotifSnoozed(delay: Duration) {
        verify(exactly = 1) {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                mockTime.intTimeMS() + delay.inWholeMilliseconds,
                AlarmManagerService.WINDOW_LENGTH_MS,
                any(),
            )
        }
    }

    private fun verifyNoNotifSnoozed() {
        verify(exactly = 0) {
            alarmManager.setWindow(AlarmManager.RTC_WAKEUP, any(), any(), any())
        }
    }

    private fun verifyPastNotifCleared(reviewReminder: ReviewReminder) {
        verify(exactly = 1) {
            notificationManager.cancel(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reviewReminder.id.value)
        }
    }
}
