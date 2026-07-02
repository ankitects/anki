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
import com.ichi2.testutils.ext.storeReminders
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
import kotlin.time.Duration.Companion.days

@RunWith(AndroidJUnit4::class)
class NotificationServiceTest : RobolectricTest() {
    companion object {
        private val yesterday = MockTime(TimeManager.time.intTimeMS() - 1.days.inWholeMilliseconds, step = 1000)
        private val today = MockTime(TimeManager.time.intTimeMS(), step = 1000)
    }

    private lateinit var context: Context
    private lateinit var notificationManager: NotificationManager

    @Before
    override fun setUp() {
        super.setUp()
        TimeManager.resetWith(today)
        context = spyk(getApplicationContext())
        notificationManager = mockk(relaxed = true)
        every { context.getSystemService(Context.NOTIFICATION_SERVICE) } returns notificationManager
        mockkObject(AlarmManagerService)
        every { AlarmManagerService.scheduleReviewReminderNotification(any(), any()) } returns Unit
        Prefs.newReviewRemindersEnabled = true
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        unmockkAll()
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
            ReviewRemindersDatabase.storeReminders(reviewReminderDeckSpecific, reviewReminderAppWide)

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

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
            val reviewReminder = createAndSaveDummyDeckSpecificReminder(did1)
            val creationTime = reviewReminder.latestNotifTime

            triggerDummyReminderNotification(reviewReminder)

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

            triggerDummyReminderNotification(reviewReminder)

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

            triggerDummyReminderNotification(reviewReminder)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminder)
            reviewReminder.verifyLatestNotifTime(previousTime = creationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with reviews today and onlyNotifyIfNoReviews is true should not fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.storeReminders(reviewReminderDeckSpecific, reviewReminderAppWide)

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verifyNoNotifsSent()
        }

    @Test
    fun `triggering with no reviews ever and onlyNotifyIfNoReviews is true should fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            val reviewReminderDeckSpecific = createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide = createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.storeReminders(reviewReminderDeckSpecific, reviewReminderAppWide)

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with review yesterday but none today and onlyNotifyIfNoReviews is true should fire notification`() =
        runTest {
            TimeManager.resetWith(yesterday) // Wind back time and perform the review
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            TimeManager.resetWith(today) // Reset time to present

            val reviewReminderDeckSpecific =
                createTestReminder(deckId = did1, thresholdInt = 1, onlyNotifyIfNoReviews = true)
            val reviewReminderAppWide =
                createTestReminder(thresholdInt = 1, onlyNotifyIfNoReviews = true)
            ReviewRemindersDatabase.storeReminders(
                reviewReminderDeckSpecific,
                reviewReminderAppWide,
            )

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
        }

    @Test
    fun `triggering with onlyNotifyIfNoReviews is false should always fire notification`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNote()
            val reviewReminderDeckSpecific = createAndSaveDummyDeckSpecificReminder(did1)
            val reviewReminderAppWide = createAndSaveDummyAppWideReminder()

            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)
            col.sched.answerCard(col.sched.card!!, CardAnswer.Rating.GOOD)
            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verifyNotifSent(reviewReminderDeckSpecific, times = 2)
            verifyNotifSent(reviewReminderAppWide, times = 2)
        }

    @Test
    fun `triggering with blocked collection should not fire notification but schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createAndSaveDummyDeckSpecificReminder(did1)
            val reviewReminderAppWide = createAndSaveDummyAppWideReminder()
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime

            CollectionManager.emulatedOpenFailure = CollectionManager.CollectionOpenFailure.LOCKED
            triggerDummyReminderNotification(reviewReminderDeckSpecific)
            triggerDummyReminderNotification(reviewReminderAppWide)

            verifyNoNotifsSent()
            verifyNextNotifScheduled(reviewReminderDeckSpecific)
            verifyNextNotifScheduled(reviewReminderAppWide)
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = true)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = true)
        }

    @Test
    fun `triggering with snoozed notification should fire notification but not schedule next`() =
        runTest {
            val did1 = addDeck("Deck", setAsSelected = true).withNotes(count = 2)
            val reviewReminderDeckSpecific = createAndSaveDummyDeckSpecificReminder(did1)
            val reviewReminderAppWide = createAndSaveDummyAppWideReminder()
            val deckSpecificCreationTime = reviewReminderDeckSpecific.latestNotifTime
            val appWideCreationTime = reviewReminderAppWide.latestNotifTime

            triggerDummyReminderNotification(reviewReminderDeckSpecific, isRecurring = false)
            triggerDummyReminderNotification(reviewReminderAppWide, isRecurring = false)

            verifyNotifSent(reviewReminderDeckSpecific)
            verifyNotifSent(reviewReminderAppWide)
            verifyNextNotifNotScheduled()
            reviewReminderDeckSpecific.verifyLatestNotifTime(previousTime = deckSpecificCreationTime, shouldHaveUpdated = false)
            reviewReminderAppWide.verifyLatestNotifTime(previousTime = appWideCreationTime, shouldHaveUpdated = false)
        }

    @Test
    fun `snooze actions of different notifications and different intervals should be different`() =
        runTest {
            val did1 = addDeck("Deck1").withNotes(count = 2)
            val did2 = addDeck("Deck2").withNotes(count = 2)
            val reviewReminderOne = createTestReminder(deckId = did1, thresholdInt = 1)
            val reviewReminderTwo = createTestReminder(deckId = did2, thresholdInt = 1)
            ReviewRemindersDatabase.storeReminders(reviewReminderOne, reviewReminderTwo)

            val slotOne = slot<Notification>()
            val slotTwo = slot<Notification>()

            triggerDummyReminderNotification(reviewReminderOne)
            triggerDummyReminderNotification(reviewReminderTwo)

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

    private fun createAndSaveDummyDeckSpecificReminder(did: DeckId): ReviewReminder {
        val reviewReminder = createTestReminder(deckId = did, thresholdInt = 1)
        ReviewRemindersDatabase.storeReminders(reviewReminder)
        return reviewReminder
    }

    private fun createAndSaveDummyAppWideReminder(): ReviewReminder {
        val reviewReminder = createTestReminder(thresholdInt = 1)
        ReviewRemindersDatabase.storeReminders(reviewReminder)
        return reviewReminder
    }

    private suspend fun triggerDummyReminderNotification(
        reviewReminder: ReviewReminder,
        isRecurring: Boolean = true,
    ) {
        NotificationService.handleReviewReminderNotification(
            context,
            reviewReminder,
            isRecurringNotification = isRecurring,
        )
    }

    /**
     * Helper method for creating a review reminder to minimize verbosity in this file.
     */
    private fun createTestReminder(
        deckId: DeckId? = null,
        thresholdInt: Int = 1,
        onlyNotifyIfNoReviews: Boolean = false,
    ) = ReviewReminder.createReviewReminder(
        time = ReviewReminderTime(hour = 12, minute = 0),
        cardTriggerThreshold = ReviewReminderCardTriggerThreshold(thresholdInt),
        scope = if (deckId != null) DeckSpecific(deckId) else Global,
        onlyNotifyIfNoReviews = onlyNotifyIfNoReviews,
    )

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
        verify(exactly = 0) { AlarmManagerService.scheduleReviewReminderNotification(any(), any()) }
    }

    private fun verifyNextNotifScheduled(reminder: ReviewReminder) {
        verify(
            exactly = 1,
        ) { AlarmManagerService.scheduleReviewReminderNotification(context, reminder) }
    }

    private fun ReviewReminder.verifyLatestNotifTime(
        previousTime: EpochMilliseconds,
        shouldHaveUpdated: Boolean,
    ) {
        val storedReminderGroup =
            when (scope) {
                is DeckSpecific -> ReviewRemindersDatabase.getRemindersForDeck(scope.did)
                is Global -> ReviewRemindersDatabase.getAllAppWideReminders()
            }
        val storedReminder = storedReminderGroup.getRemindersList().find { it.id == id }!!

        if (shouldHaveUpdated) {
            assertThat(previousTime, not(equalTo(storedReminder.latestNotifTime)))
        } else {
            assertThat(previousTime, equalTo(storedReminder.latestNotifTime))
        }
    }
}
