// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

// TODO: move out of multiprofile, restarting the app isn't specific to profiles
package com.ichi2.anki.multiprofile

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationManagerCompat
import androidx.work.WorkManager
import androidx.work.await
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.backupLock
import com.ichi2.anki.common.destinations.LauncherDestination
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import com.ichi2.anki.reviewreminders.ReviewReminderId
import com.ichi2.anki.toIntent
import com.ichi2.anki.worker.UniqueWorkNames
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import com.jakewharton.processphoenix.ProcessPhoenix
import kotlinx.coroutines.sync.Mutex
import timber.log.Timber
import java.io.File

/**
 * Restarts the app once background work has finished and the collection is closed.
 *
 * Sync work is cancelled once the sync lock is held, since it carries the current
 * profile's AnkiWeb key. The work locks stay held until ProcessPhoenix kills this
 * process, so nothing can start and reopen the collection in the meantime.
 */
suspend fun safeRestartApp(context: Context) {
    Timber.i("Restarting the app")
    syncLock.holdUnlessThrows {
        WorkManager.getInstance(context).run {
            cancelUniqueWork(UniqueWorkNames.SYNC).await()
            cancelUniqueWork(UniqueWorkNames.SYNC_MEDIA).await()
        }
        mediaSyncLock.holdUnlessThrows {
            backupLock.holdUnlessThrows {
                var unscheduledReminders = emptySet<ReviewReminderId>()
                try {
                    CollectionManager.withClosedCollection {
                        unscheduledReminders = ReviewReminderAlarmManager.unscheduleAllReviewReminderNotifications(context)
                        NotificationManagerCompat.from(context).cancelAll()
                        ProcessPhoenix.triggerRebirth(context, LauncherDestination.toIntent(context))
                        ReviewReminderAlarmManager.unscheduleAllSnoozedNotifications(context)
                    }
                } catch (e: Exception) {
                    ReviewReminderAlarmManager.rescheduleReviewReminderNotifications(context, unscheduledReminders)
                    throw e
                }
            }
        }
    }
}

/** Holds this lock while [block] runs, and keeps holding it unless [block] throws. */
private suspend inline fun Mutex.holdUnlessThrows(block: () -> Unit) {
    lock()
    var returned = false
    try {
        block()
        returned = true
    } finally {
        if (!returned) unlock()
    }
}

/** True in the short-lived ProcessPhoenix process, which only relaunches the app. */
fun isPhoenixProcess(): Boolean = isPhoenixProcessName(currentProcessName())

@VisibleForTesting
internal fun isPhoenixProcessName(name: String?): Boolean = name?.endsWith(":phoenix") == true

private fun currentProcessName(): String? = runCatching { File("/proc/self/cmdline").readText().trim { it <= ' ' } }.getOrNull()
