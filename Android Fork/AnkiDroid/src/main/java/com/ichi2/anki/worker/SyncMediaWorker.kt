// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.worker

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.VisibleForTesting
import androidx.concurrent.futures.await
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import anki.sync.MediaSyncProgress
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.CommonString
import com.ichi2.anki.NotificationChannel
import com.ichi2.anki.R
import com.ichi2.anki.cancelMediaSync
import com.ichi2.anki.common.permissions.canPostNotifications
import com.ichi2.anki.notifications.NotificationId
import com.ichi2.anki.receiver.CopyToClipboardReceiver
import com.ichi2.anki.sync.MeteredSyncPolicy
import com.ichi2.anki.sync.SyncAuth
import com.ichi2.anki.sync.syncMedia
import com.ichi2.anki.ui.internationalization.sentenceCase
import com.ichi2.anki.utils.ext.trySetForeground
import com.ichi2.utils.TruncatedString
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.ankiweb.rsdroid.Backend
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Held while a media sync runs. A profile switch holds it until the process dies,
 * so a media sync cannot start part way through a restart.
 */
val mediaSyncLock = Mutex()

class SyncMediaWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    private val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
    private val notificationManager: NotificationManagerCompat? =
        if (canPostNotifications(context)) {
            NotificationManagerCompat.from(context)
        } else {
            null
        }

    override suspend fun doWork(): Result {
        Timber.v("SyncMediaWorker::doWork")
        return mediaSyncLock.withLockUnlessSwitching { doWorkHoldingLock() }
    }

    private suspend fun doWorkHoldingLock(): Result {
        try {
            val auth = requireNotNull(inputData.toSyncAuth())

            // The collection must be open, but we should not block collection operations while
            // `syncMedia` is executing, the app should be usable during a background media sync
            val backend = CollectionManager.getColUnsafe().backend
            backend.syncMedia(auth)

            delay(1.seconds) // avoid notifications if sync occurs too quickly
            if (backend.mediaSyncStatus().active) {
                Timber.i("Showing SyncMediaWorker's notification")
                trySetForeground(getForegroundInfo())
                monitorProgress(backend)
            }
        } catch (cancellationException: CancellationException) {
            Timber.w(cancellationException, "SyncMediaWorker cancelled (user tapped Cancel or WorkManager cancelled)")
            notificationManager?.cancel(NotificationId.SYNC_MEDIA)
            Timber.d("SyncMediaWorker: progress notification cancelled after worker cancellation")
            cancelMediaSync(CollectionManager.getBackend())
            throw cancellationException
        } catch (throwable: Throwable) {
            Timber.w(throwable, "SyncMediaWorker failed")
            notify {
                // TODO: add a contentIntent, so tapping the notification opens the app
                //  (ideally showing the media sync log: an AlertDialog in DeckPicker)
                setContentTitle(TR.syncMediaFailed())
                throwable.localizedMessage?.let { message ->
                    setContentText(message)
                    setStyle(
                        NotificationCompat
                            .BigTextStyle()
                            .bigText(message),
                    )
                    addAction(
                        R.drawable.baseline_content_copy_24,
                        with(applicationContext) { TR.sentenceCase.copyToClipboard },
                        getCopyToClipboardIntent(TruncatedString.from(message)),
                    )
                }
            }
            Timber.d("SyncMediaWorker: showing failure notification")
            return Result.failure()
        }
        Timber.d("SyncMediaWorker: cancelling progress notification (sync completed)")
        notificationManager?.cancel(NotificationId.SYNC_MEDIA)

        Timber.d("SyncMediaWorker: success")
        return Result.success()
    }

    private suspend fun monitorProgress(backend: Backend) {
        var syncProgress: MediaSyncProgress? = null
        while (true) {
            val status = backend.mediaSyncStatus()
            if (!status.active) {
                Timber.i("Ended media sync notification updates")
                break
            }
            // avoid sending repeated notifications
            if (syncProgress != status.progress) {
                syncProgress = status.progress
                // TODO display better the result. Using setContentText leads to
                //  truncated text if it has more than two lines.
                // `added`, `removed` and `checked` already come translated from the
                val notificationText = syncProgress.run { "$added $removed $checked" }
                notify(getProgressNotification(notificationText))
            }
            delay(NOTIFICATION_UPDATE_RATE)
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val title = applicationContext.getString(CommonString.syncing_media)
        val cancelTitle = TR.syncAbortButton()
        val notification =
            buildNotification {
                setContentTitle(title)
                setOngoing(true)
                setProgress(0, 0, true)
                addAction(R.drawable.close_icon, cancelTitle, cancelIntent)
                foregroundServiceBehavior = NotificationCompat.FOREGROUND_SERVICE_DEFERRED
            }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NotificationId.SYNC_MEDIA, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NotificationId.SYNC_MEDIA, notification)
        }
    }

    @VisibleForTesting
    internal fun getCopyToClipboardIntent(text: TruncatedString): PendingIntent {
        val intent = CopyToClipboardReceiver.getIntent(applicationContext, text)
        return PendingIntent.getBroadcast(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun notify(notification: Notification) = notificationManager?.notify(NotificationId.SYNC_MEDIA, notification)

    private fun notify(builder: NotificationCompat.Builder.() -> Unit) {
        notify(buildNotification(builder))
    }

    private fun buildNotification(block: NotificationCompat.Builder.() -> Unit): Notification =
        NotificationCompat
            .Builder(applicationContext, NotificationChannel.SYNC.id)
            .apply {
                priority = NotificationCompat.PRIORITY_LOW
                setSmallIcon(R.drawable.ic_star_notify)
                setCategory(NotificationCompat.CATEGORY_PROGRESS)
                setSilent(true)
                block()
            }.build()

    private fun getProgressNotification(progress: CharSequence): Notification {
        val title = applicationContext.getString(CommonString.syncing_media)
        val cancelTitle = TR.syncAbortButton()

        return buildNotification {
            setContentTitle(title)
            setContentText(progress)
            setOngoing(true)
            addAction(R.drawable.close_icon, cancelTitle, cancelIntent)
        }
    }

    companion object {
        val NOTIFICATION_UPDATE_RATE = 500.milliseconds

        /** Ensure a metered network request approval only affects the intended request. */
        private val schedulingLock = Mutex()

        fun getWorkRequest(
            auth: SyncAuth,
            networkType: NetworkType = MeteredSyncPolicy.getNetworkTypeRequiredForSync(forMedia = true),
            id: UUID = UUID.randomUUID(),
        ): OneTimeWorkRequest {
            val constraints =
                Constraints
                    .Builder()
                    .setRequiredNetworkType(networkType)
                    .build()

            return OneTimeWorkRequestBuilder<SyncMediaWorker>()
                .setId(id)
                .setInputData(auth.toWorkData())
                .setConstraints(constraints)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
        }

        /**
         * A later metered approval must also release media already waiting for Wi-Fi.
         * Updating the existing request preserves any sync that has started in the meantime.
         */
        suspend fun start(
            context: Context,
            auth: SyncAuth,
            networkType: NetworkType = MeteredSyncPolicy.getNetworkTypeRequiredForSync(forMedia = true),
        ) {
            schedulingLock.withLock {
                Timber.i("Launching background media sync")
                val request = getWorkRequest(auth, networkType)

                val manager = WorkManager.getInstance(context)
                manager.enqueueUniqueWork(UniqueWorkNames.SYNC_MEDIA, ExistingWorkPolicy.KEEP, request).result.await()
                if (networkType == NetworkType.CONNECTED) {
                    manager.allowMeteredMediaSync(auth)
                }
            }
        }

        /**
         * Applies a new request's permission to use metered networks to retained media work.
         *
         * If a new request allows metered networks, it does not overwrite the old work due to
         * [ExistingWorkPolicy.KEEP], so explicitly update the work request.
         *
         * Call after `enqueueUniqueWork` has completed while holding [schedulingLock].
         */
        private suspend fun WorkManager.allowMeteredMediaSync(auth: SyncAuth) {
            val existing =
                getWorkInfosForUniqueWork(UniqueWorkNames.SYNC_MEDIA).await().firstOrNull {
                    !it.state.isFinished && it.constraints.requiredNetworkType == NetworkType.UNMETERED
                } ?: return
            val result = updateWork(getWorkRequest(auth, NetworkType.CONNECTED, existing.id)).await()
            Timber.i("Allowing metered media sync for work %s: %s", existing.id, result)
        }
    }
}
