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

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider.getApplicationContext
import androidx.test.ext.junit.runners.AndroidJUnit4
import anki.scheduler.CardAnswer
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.libanki.DeckId
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderCardTriggerThreshold
import com.ichi2.anki.reviewreminders.ReviewReminderScope.DeckSpecific
import com.ichi2.anki.reviewreminders.ReviewReminderScope.Global
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.settings.Prefs
import io.mockk.CapturingSlot
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.not
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Calendar
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

@RunWith(AndroidJUnit4::class)
class NotificationServiceTest : RobolectricTest() {
    companion object {
        private val yesterday = MockTime(TimeManager.time.intTimeMS() - 1.days.inWholeMilliseconds)
        private val today = MockTime(TimeManager.time.intTimeMS())
    }

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    override fun setUp() {
        super.setUp()
        TimeManager.resetWith(yesterday)
        context = spyk(getApplicationContext())
        notificationManager = mockk(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        mockkObject(AlarmManagerService)
        every { AlarmManagerService.scheduleReviewReminderNotification(any(), any(), any()) } returns Unit
        Prefs.newReviewRemindersEnabled = true
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
    fun `triggering with less cards than card threshold should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 3)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 3)
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminderDeckSpecific)
            verifyNextNotifScheduled(reviewReminderAppWide)
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = true)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with happy path for single deck should fire notification and schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNotifSent(reviewReminder)
            verifyNextNotifScheduled(reviewReminder)
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with happy path for global reminder should fire notification and schedule next`() =
        runTest {
            addDeck("Deck1").withNotes(count = 2)
            addDeck("Deck2").withNotes(count = 2)
            val reviewReminder = createTestReminder(thresholdInt = 4)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNotifSent(reviewReminder)
            verifyNextNotifScheduled(reviewReminder)
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with non-existent deck should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck")
            val reviewReminder = createTestReminder(deckId = did1 + 1)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminder)
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with reminder which is not present in database should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `triggering with reminder which is disabled should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, enabled = false)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `triggering with scheduled time in the future should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = 1.minutes)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            // Here, we do NOT resetWith(today) in order to test what happens if a notification is triggered before its scheduled time
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = false)
        }

    @Test
    fun `triggering with scheduled time equal to creation time should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = 0.minutes)
            val creationTime = reviewReminder.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            // Here, we do NOT resetWith(today) in order to test what happens if a notification is scheduled at its creation time
            attemptNotif(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = false)
        }

    @Test
    fun `triggering with reviews today and onlyNotifyIfNoReviews is true should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNoNotifsSent()
        }

    @Test
    fun `triggering with reviews today and onlyNotifyIfNoReviews is false should fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = false)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = false)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with no reviews ever should fire notification regardless of onlyNotifyIfNoReviews`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = false)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with review yesterday but none today should fire notification regardless of onlyNotifyIfNoReviews`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)

            val reviewReminderDeckSpecific =
                createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = false)
            val reviewReminderAppWide =
                createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with blocked collection should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1)
            val reviewReminderAppWide = createTestReminder()
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            TimeManager.resetWith(today)
            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            attemptNotif(reviewReminderDeckSpecific)
            attemptNotif(reviewReminderAppWide)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminderDeckSpecific)
            verifyNextNotifScheduled(reviewReminderAppWide)
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = true)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with snoozed notification should fire notification but not schedule next regardless of recurring firing time`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = (-1).minutes)
            val reviewReminderAppWide = createTestReminder(scheduledTimeOffsetFromNow = 1.minutes)
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime
            ReviewRemindersDatabase.insertReminder(reviewReminderDeckSpecific)
            ReviewRemindersDatabase.insertReminder(reviewReminderAppWide)

            attemptNotif(reviewReminderDeckSpecific, isRecurring = false)
            attemptNotif(reviewReminderAppWide, isRecurring = false)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
            verifyNextNotifNotScheduled()
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = false)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = false)
        }

    @Test
    fun `triggering with snoozed notification not stored in database should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, scheduledTimeOffsetFromNow = (-1).minutes)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder, isRecurring = false)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `triggering with snoozed notification which is disabled should not fire notification nor schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminder = createTestReminder(deckId = did1, enabled = false, scheduledTimeOffsetFromNow = (-1).minutes)
            ReviewRemindersDatabase.insertReminder(reviewReminder)

            TimeManager.resetWith(today)
            attemptNotif(reviewReminder, isRecurring = false)

            verifyNoNotifsSent()
            verifyNextNotifNotScheduled()
        }

    @Test
    fun `snooze actions of different notifications and different intervals should be different`() =
        runTest {
            val did1 = addDeck("Deck1").withNotes(count = 2)
            val did2 = addDeck("Deck2").withNotes(count = 2)
            val reviewReminderOne = createTestReminder(deckId = did1, thresholdInt = 1)
            val reviewReminderTwo = createTestReminder(deckId = did2, thresholdInt = 1)
            ReviewRemindersDatabase.insertReminder(reviewReminderOne)
            ReviewRemindersDatabase.insertReminder(reviewReminderTwo)

            val slotOne = slot<Notification>()
            val slotTwo = slot<Notification>()

            TimeManager.resetWith(today)
            attemptNotif(reviewReminderOne)
            attemptNotif(reviewReminderTwo)

            verifyNotifSent(reviewReminderOne, slot = slotOne)
            verifyNotifSent(reviewReminderTwo, slot = slotTwo)

            val snoozeIntents =
                setOf(
                    slotOne.captured.actions[0].actionIntent,
                    slotOne.captured.actions[1].actionIntent,
                    slotTwo.captured.actions[0].actionIntent,
                    slotTwo.captured.actions[1].actionIntent,
                )
            assertThat(snoozeIntents.size, equalTo(4))
        }

    private suspend fun attemptNotif(
        reviewReminder: ReviewReminder,
        isRecurring: Boolean = true,
    ) {
        NotificationService.handleReviewReminderNotification(
            context,
            reviewReminder.id,
            reviewReminder.scope,
            isRecurringNotification = isRecurring,
        )
    }

    /**
     * Helper method for creating a review reminder to minimize verbosity in this file.
     * The review reminder's scheduled time is initialized to be equal to [today] offset by
     * [scheduledTimeOffsetFromNow].
     */
    private fun createTestReminder(
        deckId: DeckId? = null,
        thresholdInt: Int = 1,
        enabled: Boolean = true,
        onlyNotifyIfNoReviews: Boolean = false,
        scheduledTimeOffsetFromNow: Duration = (-1).minutes,
    ): ReviewReminder {
        val scheduledTime = TimeManager.time.calendar().clone() as Calendar
        scheduledTime.add(Calendar.MINUTE, scheduledTimeOffsetFromNow.inWholeMinutes.toInt())

        return ReviewReminder.createReviewReminder(
            time =
                ReviewReminderTime(
                    scheduledTime.get(Calendar.HOUR_OF_DAY),
                    scheduledTime.get(Calendar.MINUTE),
                ),
            cardTriggerThreshold = ReviewReminderCardTriggerThreshold(thresholdInt),
            scope = if (deckId != null) DeckSpecific(deckId) else Global,
            enabled = enabled,
            onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
        )
    }

    private fun verifyNoNotifsSent() {
        verify(exactly = 0) { notificationManager.notify(any(), any(), any()) }
    }

    private fun verifyNotifSent(
        reminder: ReviewReminder,
        times: Int = 1,
        slot: CapturingSlot<Notification>? = null,
    ) {
        if (slot != null) {
            verify(
                exactly = times,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reminder.id.value, capture(slot)) }
        } else {
            verify(
                exactly = times,
            ) { notificationManager.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, reminder.id.value, any()) }
        }
    }

    private fun verifyNextNotifNotScheduled() {
        verify(exactly = 0) { AlarmManagerService.scheduleReviewReminderNotification(any(), any(), any()) }
    }

    private fun verifyNextNotifScheduled(reminder: ReviewReminder) {
        verify(exactly = 1) {
            AlarmManagerService.scheduleReviewReminderNotification(
                context,
                match { it.id == reminder.id },
                attemptImmediateNotification = false,
            )
        }
    }

    private suspend fun ReviewReminder.verifyLatestNotifTime(
        previousTime: EpochMilliseconds,
        shouldHaveUpdated: Boolean,
    ) {
        val storedReminder =
            ReviewRemindersDatabase
                .getAllReminders()
                .getRemindersList()
                .find { it.id == id }!!

        if (shouldHaveUpdated) {
            assertThat(previousTime, not(equalTo(storedReminder.latestNotifTime)))
        } else {
            assertThat(previousTime, equalTo(storedReminder.latestNotifTime))
        }
    }
}
