// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.reviewreminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.android.AlarmManagement
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
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

@RunWith(AndroidJUnit4::class)
class ReviewReminderAlarmManagerTest : RobolectricTest() {
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
    private lateinit var reviewReminder: ReviewReminder

    @Before
    override fun setUp() {
        super.setUp()
        context = mockk(relaxed = true)
        alarmManager = mockk(relaxed = true)
        every { context.getSystemService<AlarmManager>() } returns alarmManager

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
        ReviewReminderAlarmManager.scheduleReviewReminderNotification(context, reviewReminder, attemptImmediateNotification = true)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                expectedSchedulingTime.timeInMillis,
                AlarmManagement.WINDOW_LENGTH_MS,
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
        ReviewReminderAlarmManager.scheduleReviewReminderNotification(context, pastTimeReviewReminder, attemptImmediateNotification = false)
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                expectedSchedulingTime.timeInMillis,
                AlarmManagement.WINDOW_LENGTH_MS,
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
        ReviewReminderAlarmManager.scheduleReviewReminderNotification(
            context,
            currentTimeReviewReminder,
            attemptImmediateNotification = true,
        )
        verify {
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                expectedSchedulingTime.timeInMillis,
                AlarmManagement.WINDOW_LENGTH_MS,
                any(),
            )
        }
    }

    @Test
    fun `scheduleReviewReminderNotifications attempts immediate notification when flag is true`() {
        ReviewReminderAlarmManager.scheduleReviewReminderNotification(context, reviewReminder, attemptImmediateNotification = true)

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
        ReviewReminderAlarmManager.scheduleReviewReminderNotification(context, reviewReminder, attemptImmediateNotification = false)

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

        ReviewReminderAlarmManager.unscheduleReviewReminderNotifications(context, reviewReminder)
        verify { alarmManager.cancel(pendingIntent) }
    }

    @Test
    fun `scheduleAllEnabledReviewReminderNotifications schedules and fires reminders for all enabled reminders in database`() =
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

            ReviewReminderAlarmManager.scheduleAllEnabledReviewReminderNotifications(context)

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
                        AlarmManagement.WINDOW_LENGTH_MS,
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
}
