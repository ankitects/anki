/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import androidx.annotation.VisibleForTesting
import androidx.core.app.PendingIntentCompat
import androidx.core.content.getSystemService
import anki.collection.opChanges
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.launchCatching
import com.ichi2.anki.libanki.EpochMilliseconds
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.anki.services.AlarmManagerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import timber.log.Timber
import java.util.Date
import kotlin.time.Duration.Companion.milliseconds

/**
 * Schedules a wake-up alarm at the collection's next [dayCutoff][Scheduler.dayCutoff] and refreshes
 * widgets when it fires.
 *
 * Manifest-declared so it survives normal process death.
 * Does not handle `FLAG_STOPPED` (force-stop or aggressive OEM swipe-to-kill/battery optimizations)
 */
class DayRolloverAlarm : AnkiBroadcastReceiver() {
    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        applicationScope.launchCatchingIoWithReport("DayRolloverAlarm::onReceive") {
            Timber.i("DayRolloverAlarm received %s", intent.action)
            when (intent.action) {
                ACTION_ROLLOVER -> {
                    Timber.i("ACTION_ROLLOVER: Updating widgets")
                    ChangeManager.notifySubscribers(opChanges { studyQueues = true }, initiator = null)
                    runCatching { WidgetStatus.updateInBackground(context) }.onFailure { Timber.w(it) }
                    scheduleNextInternal(context)
                }
                ACTION_TIMEZONE_CHANGED, ACTION_TIME_CHANGED -> scheduleNextInternal(context)
            }
        }
    }

    companion object {
        /** Custom action used for when [dayCutoff][Scheduler.dayCutoff] is reached. */
        const val ACTION_ROLLOVER = "com.ichi2.widget.ACTION_ROLLOVER"

        /**
         * Set an alarm at the next `dayCutoff`.
         * Idempotent (repeated calls update the existing pending intent).
         */
        fun scheduleNext(context: Context) {
            applicationScope.launchCatchingIoWithReport("DayRolloverAlarm::scheduleNext") {
                scheduleNextInternal(context)
            }
        }

        @VisibleForTesting
        internal suspend fun scheduleNextInternal(context: Context) {
            val cutoffMs = nextFutureCutoffMs() ?: return
            val pendingIntent = context.buildPendingIntent(ACTION_ROLLOVER) ?: return
            val alarmManager = context.getSystemService<AlarmManager>() ?: return
            // TODO: This uses setWindow with a 10 minute window, so likely fires at 04:10, rather than 04:00
            // Consider requesting SCHEDULE_EXACT_ALARM,
            alarmManager.setWindowSafe(
                type = AlarmManager.RTC_WAKEUP,
                windowStartMillis = cutoffMs,
                windowLengthMillis = AlarmManagerService.WINDOW_LENGTH_MS,
                operation = pendingIntent,
            )
        }

        /**
         * The next `[Scheduler.dayCutoff]`, or `null` if the alarm should not be set.
         */
        private suspend fun nextFutureCutoffMs(): EpochMilliseconds? {
            val cutoffMs = (withOpenColOrNull { sched.dayCutoff } ?: return null) * 1000L
            if (cutoffMs <= TimeManager.time.intTimeMS()) {
                Timber.w("dayCutoff %d is not in the future; skipping", cutoffMs)
                return null
            }
            return cutoffMs
        }

        private fun Context.buildPendingIntent(
            @Suppress("SameParameterValue") action: String,
        ): PendingIntent? =
            PendingIntentCompat.getBroadcast(
                this,
                0,
                Intent(this, DayRolloverAlarm::class.java).apply { this.action = action },
                PendingIntent.FLAG_UPDATE_CURRENT,
                false,
            )
    }
}

/**
 * [AlarmManager.setWindow] implementation which catches exceptions.
 */
private fun AlarmManager.setWindowSafe(
    type: Int,
    windowStartMillis: EpochMilliseconds,
    windowLengthMillis: Long,
    operation: PendingIntent,
) = try {
    setWindow(type, windowStartMillis, windowLengthMillis, operation)
    val delta = (windowStartMillis - TimeManager.time.intTimeMS()).milliseconds
    Timber.i("scheduled day rollover")
    Timber.d("rollover alarm scheduled for %s (in %s)", Date(windowStartMillis), delta)
} catch (e: SecurityException) {
    // #6332 - Too Many Alarms on Samsung Devices
    Timber.w(e, "too many alarms — could not schedule alarm")
} catch (e: Exception) {
    Timber.w(e, "failed to schedule alarm")
}

/**
 * Launches [block] on [Dispatchers.IO], reporting exceptions to ACRA.
 *
 * @param origin origin in the crash report
 */
private fun CoroutineScope.launchCatchingIoWithReport(
    origin: String,
    block: suspend () -> Unit,
) {
    launchCatching(Dispatchers.IO, errorMessageHandler = { msg ->
        CrashReportService.sendExceptionReport(
            e = ManuallyReportedException(msg),
            origin = origin,
            onlyIfSilent = true,
        )
    }) {
        block()
    }
}
