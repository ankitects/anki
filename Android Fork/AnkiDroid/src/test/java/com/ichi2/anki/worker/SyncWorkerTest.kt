// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import android.app.Notification
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker.Result
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import anki.collection.Progress
import anki.collection.progress
import anki.sync.SyncAuth
import anki.sync.SyncCollectionResponse
import anki.sync.syncCollectionResponse
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import net.ankiweb.rsdroid.Backend
import net.ankiweb.rsdroid.BackendFactory
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SyncWorkerTest : RobolectricTest() {
    /** Direct worker notifications only: the test foreground updater does not start Android's foreground service. */
    private val syncNotification: Notification?
        get() = shadowOf(targetContext.getSystemService<NotificationManager>()!!).getNotification(NotificationId.SYNC)

    /** Restores the default backend factory after each test. */
    @After
    fun resetBackendFactory() {
        BackendFactory.setOverride(null)
    }

    @Test
    fun `sync completion waits for the final progress update before removing notification`() =
        runTest {
            val work = runSyncWithBlockedProgress()

            assertEquals(Result.success(), work.await())
            assertNull(syncNotification)
        }

    @Test
    fun `sync failure waits for the final progress update before showing error`() =
        runTest {
            val work = runSyncWithBlockedProgress(syncError = IllegalStateException("sync failed"))

            assertEquals(Result.failure(), work.await())
            val notification = assertNotNull(syncNotification)
            assertEquals("sync failed", notification.extras.getString(Notification.EXTRA_TEXT))
            assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT == 0)
        }

    @Test
    fun `sync cancellation waits for the final progress update before removing notification`() =
        runTest {
            val work = runSyncWithBlockedProgress(cancelWorker = true)

            work.join()
            assertTrue(work.isCancelled)
            assertNull(syncNotification)
        }

    @Test
    fun `failed sync advances the retry timestamp`() =
        runTest {
            val completedAt = 1_600_000_000_000L
            TimeManager.resetWith(MockTime(completedAt))
            Prefs.lastSyncTime = 0

            val work = runSyncWithBlockedProgress(syncError = IllegalStateException("sync failed"))

            assertThat(work.await(), equalTo(Result.failure()))
            assertThat(Prefs.lastSyncTime, equalTo(completedAt))
        }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `sync supplies a foreground notification when direct notifications are disabled`() =
        runTest {
            shadowOf(targetContext.getSystemService<NotificationManager>()!!).setNotificationsEnabled(false)
            val foregroundUpdates = mutableListOf<ForegroundInfo>()

            val work = runSyncWithBlockedProgress(onForeground = { foregroundUpdates.add(it) })

            assertEquals(Result.success(), work.await())
            assertForegroundSyncNotification(foregroundUpdates.single())
            assertNull(syncNotification, "the worker must not post direct progress notifications")
        }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun `failed sync still advances the retry timestamp when notifications are disabled`() =
        runTest {
            shadowOf(targetContext.getSystemService<NotificationManager>()!!).setNotificationsEnabled(false)
            val completedAt = 1_600_000_000_000L
            TimeManager.resetWith(MockTime(completedAt))
            Prefs.lastSyncTime = 0
            val foregroundUpdates = mutableListOf<ForegroundInfo>()

            val work =
                runSyncWithBlockedProgress(
                    syncError = IllegalStateException("sync failed"),
                    onForeground = { foregroundUpdates.add(it) },
                )

            assertThat(work.await(), equalTo(Result.failure()))
            assertThat(Prefs.lastSyncTime, equalTo(completedAt))
            assertForegroundSyncNotification(foregroundUpdates.single())
            assertNull(syncNotification, "the worker must not post a direct error notification")
        }

    @Test
    fun `cancelled sync advances the retry timestamp`() =
        runTest {
            val completedAt = 1_600_000_000_000L
            TimeManager.resetWith(MockTime(completedAt))
            Prefs.lastSyncTime = 0

            val work = runSyncWithBlockedProgress(cancelWorker = true)

            work.join()
            assertThat(work.isCancelled, equalTo(true))
            assertThat(Prefs.lastSyncTime, equalTo(completedAt))
        }

    /** Runs sync with a paused progress update, checks cleanup ordering, then releases the update. */
    private suspend fun TestScope.runSyncWithBlockedProgress(
        syncError: Exception? = null,
        cancelWorker: Boolean = false,
        onForeground: (ForegroundInfo) -> Unit = {},
    ): Deferred<Result> {
        val releaseProgress = CountDownLatch(1)
        setUpSyncBackend(releaseProgress, syncError)
        val worker =
            TestListenableWorkerBuilder<SyncWorker>(targetContext, workDataOf("hkey" to "test"))
                .setForegroundUpdater { _, _, foregroundInfo ->
                    onForeground(foregroundInfo)
                    CallbackToFutureAdapter.getFuture<Void> { completer ->
                        completer.set(null)
                        "SyncWorkerTest foreground update"
                    }
                }.build()
        val work = async { worker.doWork() }
        try {
            assertCleanupWaitsForProgress(work, cancelWorker)
        } finally {
            releaseProgress.countDown()
        }
        return work
    }

    /** Verifies the notification supplied to WorkManager; Android's service lifecycle is outside this unit test. */
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    private fun assertForegroundSyncNotification(foregroundInfo: ForegroundInfo) {
        assertEquals(NotificationId.SYNC, foregroundInfo.notificationId)
        assertEquals(ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC, foregroundInfo.foregroundServiceType)
        val notification = foregroundInfo.notification
        assertEquals(NotificationChannel.SYNC.id, notification.channelId)
        assertNotNull(notification.smallIcon)
        assertTrue(notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    /** Installs a backend whose sync finishes while a progress update waits for [releaseProgress]. */
    private suspend fun setUpSyncBackend(
        releaseProgress: CountDownLatch,
        syncError: Exception?,
    ) {
        val progressStarted = CountDownLatch(1)
        CollectionManager.discardBackend()
        BackendFactory.setOverride {
            object : Backend() {
                override fun latestProgress(): Progress {
                    progressStarted.countDown()
                    check(releaseProgress.await(10, TimeUnit.SECONDS)) { "test did not release progress" }
                    return progress { normalSync = Progress.NormalSync.getDefaultInstance() }
                }

                override fun syncCollection(
                    auth: SyncAuth,
                    syncMedia: Boolean,
                ): SyncCollectionResponse {
                    check(progressStarted.await(10, TimeUnit.SECONDS)) { "progress monitor did not start" }
                    syncError?.let { throw it }
                    return syncCollectionResponse { required = SyncCollectionResponse.ChangesRequired.NO_CHANGES }
                }
            }
        }
    }

    /** Checks that [work] stays incomplete while progress is blocked, including after cancellation. */
    private fun TestScope.assertCleanupWaitsForProgress(
        work: Job,
        cancelWorker: Boolean,
    ) {
        runCurrent()
        assertFalse(work.isCompleted, "cleanup must wait for the in-flight progress update")
        if (cancelWorker) {
            work.cancel()
            runCurrent()
            assertFalse(work.isCompleted, "cancellation must also wait for the in-flight update")
        }
    }
}
