// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backupLock
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.libanki.LibAnki
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.backupCheck
import com.ichi2.anki.multiprofile.mediaSyncCheck
import com.ichi2.anki.multiprofile.safeRestartApp
import com.ichi2.anki.multiprofile.syncCheck
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SwitchProfilesRestartReviewTest : RobolectricTest() {
    private val app get() = AnkiDroidApp.instance

    @After
    fun releaseRestartLocks() {
        listOf(syncLock, mediaSyncLock, backupLock).filter { it.isLocked }.forEach { it.unlock() }
    }

    private fun model(restart: suspend () -> Unit = { safeRestartApp(app) }): SwitchProfilesViewModel {
        val manager = requireNotNull(app.profileManager)
        val target = manager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
        val model =
            SwitchProfilesViewModel(
                manager,
                ProfileSwitchGuard(manager, listOf(syncCheck(), mediaSyncCheck(), backupCheck())),
                restart,
            )
        model.requestSwitch(model.profiles.value.single { it.id == target })
        return model
    }

    @Test
    fun `switch removes the old profile notification and its unscoped deck action`() =
        runTest {
            col.decks.rename(1L, "Private French")
            addNoteUsingNoteTypeName("Basic", "Private question", "Private answer")
            val reminder =
                ReviewReminder.createReviewReminder(
                    time = ReviewReminderTime(20, 0),
                    scope = ReviewReminderScope.DeckSpecific(1L),
                )
            ReviewRemindersDatabase.remindersSharedPrefs.edit(commit = true) { clear() }
            ReviewRemindersDatabase.insertReminder(reminder)
            NotificationService.handleReviewReminderNotification(app, reminder.id, reminder.scope, isRecurringNotification = false)

            val notifications = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val oldNotification =
                notifications.activeNotifications.single {
                    it.tag == NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG
                }
            assertTrue(
                oldNotification.notification.extras
                    .getCharSequence(Notification.EXTRA_TITLE)
                    .toString()
                    .contains("Private French"),
            )
            val action = shadowOf(oldNotification.notification.contentIntent).savedIntent
            assertEquals(1L, action.getLongExtra(IntentHandler.EXTRA_DECK_ID, -1L))
            assertEquals(setOf(IntentHandler.EXTRA_DECK_ID), action.extras!!.keySet())

            model().switchProfile()
            advanceUntilIdle()

            assertTrue(
                notifications.activeNotifications.none { it.tag == NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG },
                "Default's notification, deck name, and deck-only click action survived the switch to Work",
            )
        }

    @Test
    fun `switch unschedules outgoing profile reminder alarms`() =
        runTest {
            val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
            ReviewReminderAlarmManager.scheduleReviewReminderNotification(app, reminder, attemptImmediateNotification = false)
            val alarms = shadowOf(app.getSystemService(Context.ALARM_SERVICE) as AlarmManager)

            @Suppress("DEPRECATION")
            fun reminderAlarmIsScheduled() =
                alarms.scheduledAlarms.any {
                    shadowOf(it.operation).savedIntent.action ==
                        NotificationService.NotificationServiceAction.ScheduleRecurringNotifications.actionString
                }
            assertTrue(reminderAlarmIsScheduled())

            model().switchProfile()
            advanceUntilIdle()

            assertFalse(reminderAlarmIsScheduled(), "An alarm containing only the outgoing profile's reminder id survived restart")
        }

    @Test
    fun `a queued collection operation cannot reopen the collection before process handover`() {
        runBlocking { CollectionManager.ensureOpen() }
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
        runBlocking { ReviewRemindersDatabase.insertReminder(reminder) }
        val mainScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
        ioDispatcher = UnconfinedTestDispatcher(mainScheduler)
        val queue = QueuedDispatcher()
        val previousQueue = CollectionManager.setTestDispatcher(queue, useReentrantLock = false)
        var openAtHandover: Boolean? = null
        val observer =
            object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) {
                    openAtHandover = collectionIsOpen()
                    super.startActivity(intent)
                }
            }
        try {
            model { safeRestartApp(observer) }.switchProfile()
            mainScheduler.runCurrent()
            assertEquals(1, queue.tasks.size, "restart must be awaiting ensureClosed on the collection queue")

            val lateOperation =
                CoroutineScope(Dispatchers.Unconfined).launch {
                    // A real alarm receiver's work can be queued behind the restart's close.
                    NotificationService.handleReviewReminderNotification(app, reminder.id, reminder.scope, isRecurringNotification = false)
                }
            assertEquals(2, queue.tasks.size)
            queue.tasks.removeFirst().run()
            assertFalse(collectionIsOpen())
            queue.tasks.removeFirst().run()
            assertTrue(lateOperation.isCompleted)

            mainScheduler.runCurrent()

            assertNotNull(openAtHandover, "the real ProcessPhoenix handover must have run")
            assertFalse(openAtHandover, "the collection reopened before handing off to a process that will kill this process")
        } finally {
            CollectionManager.setTestDispatcher(previousQueue)
        }
    }

    // Read without entering the intentionally paused collection queue.
    @Suppress("DEPRECATION")
    private fun collectionIsOpen() = LibAnki.collection?.dbClosed == false

    private class QueuedDispatcher : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            tasks.addLast(block)
        }
    }
}
