// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.multiprofile

import android.app.AlarmManager
import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.content.IntentCompat
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.WorkInfo
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.IntentHandler
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backupLock
import com.ichi2.anki.libanki.LibAnki
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import com.ichi2.anki.reviewreminders.ReviewReminderScope
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.services.NotificationService
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import com.jakewharton.processphoenix.PhoenixActivity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class AppRestartTest : RobolectricTest() {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private val backgroundWorkLocks get() = listOf(syncLock, mediaSyncLock, backupLock)

    @After
    override fun tearDown() {
        backgroundWorkLocks.filter { it.isLocked }.forEach { it.unlock() }
        super.tearDown()
    }

    private fun startedActivities() = shadowOf(context as Application)

    /** The intent ProcessPhoenix relaunches once it has killed this process. */
    private fun relaunchIntent(): Intent? {
        val phoenix = startedActivities().nextStartedActivity ?: return null
        assertEquals(PhoenixActivity::class.java.name, phoenix.component?.className)
        return IntentCompat.getParcelableArrayListExtra(phoenix, "phoenix_restart_intents", Intent::class.java)?.single()
    }

    @Test
    fun `restart relaunches the launcher in a cleared task through ProcessPhoenix`() =
        runTest {
            safeRestartApp(context)

            val intent = assertNotNull(relaunchIntent(), "ProcessPhoenix must be handed the relaunch")
            assertEquals(IntentHandler::class.java.name, intent.component?.className)
            assertTrue(intent.categories.contains(Intent.CATEGORY_LAUNCHER))
            assertTrue(intent.flags and Intent.FLAG_ACTIVITY_CLEAR_TASK != 0, "the old profile's activities must not survive")
        }

    @Test
    fun `restart closes the collection before handing over to ProcessPhoenix`() =
        runTest {
            CollectionManager.ensureOpen()

            safeRestartApp(context)

            assertFalse(CollectionManager.isOpenUnsafe(), "the process is killed next, and a write in flight could corrupt it")
        }

    @Test
    fun `an operation queued during the restart cannot reopen the collection before the handover`() =
        runTest {
            CollectionManager.ensureOpen()
            val queue = QueuedDispatcher()
            val previousQueue = CollectionManager.setTestDispatcher(queue, useReentrantLock = false)
            var openAtHandover: Boolean? = null
            val observer =
                object : ContextWrapper(context) {
                    override fun startActivity(intent: Intent) {
                        openAtHandover = collectionIsOpen()
                        super.startActivity(intent)
                    }
                }
            try {
                val restart = launch { safeRestartApp(observer) }
                advanceUntilIdle()
                val lateOperation = launch { CollectionManager.withCol { } }
                advanceUntilIdle()

                while (queue.tasks.isNotEmpty()) queue.tasks.removeFirst().run()
                advanceUntilIdle()
                restart.join()
                lateOperation.join()

                assertEquals(false, openAtHandover, "ProcessPhoenix kills this process next, so an open collection could lose a write")
            } finally {
                CollectionManager.setTestDispatcher(previousQueue)
            }
        }

    @Test
    fun `restart clears the notifications of the profile being left`() =
        runTest {
            val notifications = context.getSystemService(NotificationManager::class.java)
            notifications.notify(NotificationService.REVIEW_REMINDER_NOTIFICATION_TAG, 0, notification("Private French"))
            notifications.notify(SYNC_RESULT_NOTIFICATION_ID, notification("Sync complete"))

            safeRestartApp(context)

            assertEquals(emptyList(), notifications.activeNotifications.map { it.notification.extras.getString(Notification.EXTRA_TITLE) })
        }

    @Test
    fun `restart unschedules the recurring and snoozed reminders of the profile being left`() =
        runTest {
            val daily = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
            val deck = ReviewReminder.createReviewReminder(time = ReviewReminderTime(8, 30), scope = ReviewReminderScope.DeckSpecific(1L))
            ReviewReminderAlarmManager.scheduleReviewReminderNotification(context, daily, attemptImmediateNotification = false)
            ReviewReminderAlarmManager.scheduleReviewReminderNotification(context, deck, attemptImmediateNotification = false)
            ReviewReminderAlarmManager.scheduleSnoozedNotification(context, deck, snoozeIntervalInMinutes = 5)
            assertEquals(3, reminderAlarms().size)

            safeRestartApp(context)

            assertEquals(emptyList(), reminderAlarms(), "they would fire into the next profile's reminders")
        }

    @Test
    fun `restart leaves alarms that are not reminders alone`() =
        runTest {
            val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
            ReviewReminderAlarmManager.scheduleReviewReminderNotification(context, reminder, attemptImmediateNotification = false)
            val other =
                PendingIntentCompat.getBroadcast(
                    context,
                    reminder.id.value,
                    Intent(context, NotificationService::class.java),
                    0,
                    false,
                )!!
            context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC_WAKEUP, Long.MAX_VALUE, other)

            safeRestartApp(context)

            assertEquals(listOf(other), scheduledAlarms())
        }

    @Test
    fun `restart cancels sync work queued for the profile being left`() =
        runTest {
            context.enqueueWaiting(UniqueWorkNames.SYNC)
            context.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)

            safeRestartApp(context)

            assertEquals(WorkInfo.State.CANCELLED, context.workState(UniqueWorkNames.SYNC), "it carries this profile's AnkiWeb key")
            assertEquals(WorkInfo.State.CANCELLED, context.workState(UniqueWorkNames.SYNC_MEDIA), "it carries this profile's AnkiWeb key")
        }

    @Test
    fun `restart cancels a media sync enqueued by a sync that is still finishing`() =
        runTest {
            syncLock.lock()
            val restart = launch { safeRestartApp(context) }
            advanceUntilIdle()

            // SyncWorker enqueues the media sync before it releases syncLock
            context.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)

            syncLock.unlock()
            restart.join()

            assertEquals(
                WorkInfo.State.CANCELLED,
                context.workState(UniqueWorkNames.SYNC_MEDIA),
                "it carries this profile's AnkiWeb key",
            )
        }

    @Test
    fun `restart cancels a media sync instead of waiting for it to finish`() =
        runTest {
            mediaSyncLock.lock()
            context.enqueueWaiting(UniqueWorkNames.SYNC_MEDIA)
            val restart = launch { safeRestartApp(context) }
            advanceUntilIdle()

            assertEquals(
                WorkInfo.State.CANCELLED,
                context.workState(UniqueWorkNames.SYNC_MEDIA),
                "otherwise the restart sits through a media sync it is about to cancel",
            )

            mediaSyncLock.unlock()
            restart.join()
        }

    @Test
    fun `a cancelled restart releases only the locks it took`() =
        runTest {
            backupLock.lock()
            val restart = launch { safeRestartApp(context) }
            advanceUntilIdle()

            restart.cancelAndJoin()

            assertFalse(syncLock.isLocked, "a cancelled restart must not leave syncing blocked")
            assertFalse(mediaSyncLock.isLocked, "a cancelled restart must not leave media syncing blocked")
            assertTrue(backupLock.isLocked, "the backup that is still writing holds it")
        }

    @Test
    fun `restart keeps the locks until the process is killed`() =
        runTest {
            safeRestartApp(context)

            backgroundWorkLocks.forEach { assertTrue(it.isLocked, "work must not start before ProcessPhoenix kills the process") }
        }

    @Test
    fun `restart releases the locks if it cannot hand over to ProcessPhoenix`() =
        runTest {
            val refusesActivities =
                object : ContextWrapper(context) {
                    override fun startActivity(intent: Intent) = throw IllegalStateException("cannot start activities")
                }

            assertFailsWith<IllegalStateException> { safeRestartApp(refusesActivities) }

            backgroundWorkLocks.forEach { assertFalse(it.isLocked, "a failed restart must not leave sync and backup blocked") }
        }

    private suspend fun TestScope.assertRestartWaitsFor(
        lock: Mutex,
        why: String,
    ) {
        lock.lock()
        val restart = launch { safeRestartApp(context) }

        advanceUntilIdle()
        assertNull(startedActivities().peekNextStartedActivity(), why)

        lock.unlock()
        restart.join()
        assertNotNull(relaunchIntent(), "the restart must go ahead once the work has finished")
    }

    @Test
    fun `restart waits for a backup that is still writing`() =
        runTest { assertRestartWaitsFor(backupLock, "killing the process mid-write can leave a truncated backup") }

    @Test
    fun `restart waits for a sync that started after the checks passed`() =
        runTest { assertRestartWaitsFor(syncLock, "a sync can start between the checks and the restart") }

    @Test
    fun `restart waits for a media sync that started after the checks passed`() =
        runTest { assertRestartWaitsFor(mediaSyncLock, "a media sync can start between the checks and the restart") }

    private fun notification(title: String) =
        NotificationCompat
            .Builder(context, NotificationChannel.GENERAL.id)
            .setSmallIcon(R.drawable.ic_star_notify)
            .setContentTitle(title)
            .build()

    @Suppress("DEPRECATION")
    private fun scheduledAlarms() = shadowOf(context.getSystemService(AlarmManager::class.java)).scheduledAlarms.map { it.operation }

    private fun reminderAlarms() =
        scheduledAlarms().filter {
            shadowOf(it).savedIntent.action in
                listOf(
                    NotificationService.NotificationServiceAction.ScheduleRecurringNotifications.actionString,
                    NotificationService.NotificationServiceAction.SnoozeNotification.actionString,
                )
        }

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

    @Test
    fun `only the ProcessPhoenix process is recognised as it`() {
        assertTrue(isPhoenixProcessName("com.ichi2.anki:phoenix"))
        assertFalse(isPhoenixProcessName("com.ichi2.anki"))
        assertFalse(isPhoenixProcessName("com.ichi2.anki:acra"))
        assertFalse(isPhoenixProcessName(null))
    }

    companion object {
        private const val SYNC_RESULT_NOTIFICATION_ID = 1000
    }
}
