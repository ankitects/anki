// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.services

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.BadParcelableException
import android.os.Bundle
import android.os.Parcelable
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.android.AlarmManagement
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class SnoozeServiceTest : RobolectricTest() {
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

    @Test
    fun `getPendingIntent returns unique intents per reminder and per snooze interval`() =
        runTest {
            val did = addDeck("Deck1")
            val secondReminder =
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(21, 0),
                    scope = ReviewReminderScope.DeckSpecific(did),
                )
            val pendingIntents =
                setOf(
                    SnoozeService.getPendingIntent(
                        context,
                        reviewReminder.id,
                        reviewReminder.scope,
                        5.minutes,
                    ),
                    SnoozeService.getPendingIntent(
                        context,
                        reviewReminder.id,
                        reviewReminder.scope,
                        1.hours,
                    ),
                    SnoozeService.getPendingIntent(
                        context,
                        secondReminder.id,
                        secondReminder.scope,
                        5.minutes,
                    ),
                    SnoozeService.getPendingIntent(
                        context,
                        secondReminder.id,
                        secondReminder.scope,
                        1.hours,
                    ),
                    SnoozeService.getPendingIntent(
                        context,
                        reviewReminder.id,
                        reviewReminder.scope,
                        5.minutes,
                    ),
                )

            assertThat(pendingIntents.size, equalTo(4))
        }

    /**
     * #23084: Notifications survive app updates, so the parceled extras of their snooze intents may
     * reference classes which a newer version of the app has renamed, making them unreadable.
     */
    @SuppressLint("NewApi") // Bundle.getParcelable(String, Class) requires API 33: Robolectric runs at API 36
    @Test
    fun `receiving intent with corrupt extras should ignore snooze request`() {
        val corruptExtras =
            mockk<Bundle> {
                every { getParcelable(any(), any<Class<Parcelable>>()) } throws BadParcelableException("corrupt")
            }
        val intent = mockk<Intent> { every { extras } returns corruptExtras }

        SnoozeService().onReceive(context, intent)

        verifyNoNotifSnoozed()
        verify(exactly = 0) { notificationManager.cancel(any(), any()) }
    }

    private suspend fun attemptSnooze(
        reviewReminder: ReviewReminder,
        delay: Duration,
    ) {
        SnoozeService.handleSnoozeReviewReminder(
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
                AlarmManagement.WINDOW_LENGTH_MS,
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
