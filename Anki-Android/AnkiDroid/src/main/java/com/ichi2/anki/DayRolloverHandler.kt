/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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
 *
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/edd38ca06730d7fc16804f52ce10f6bc54c3d145/qt/aqt/main.py#L508-L528
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_TIMEZONE_CHANGED
import android.content.Intent.ACTION_TIME_CHANGED
import android.content.Intent.ACTION_TIME_TICK
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.RECEIVER_EXPORTED
import anki.collection.OpChanges
import anki.collection.opChanges
import com.ichi2.anki.CollectionManager.withOpenColOrNull
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.common.crashreporting.CrashReportService
import com.ichi2.anki.common.exception.ManuallyReportedException
import com.ichi2.anki.libanki.EpochSeconds
import com.ichi2.anki.libanki.sched.Scheduler
import com.ichi2.anki.observability.ChangeManager
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.WidgetStatus
import kotlinx.coroutines.Dispatchers
import timber.log.Timber

/**
 * Checks on time change events for 'day rollover'
 *
 * If so, notifies [ChangeManager] that [studyQueues][OpChanges.getStudyQueues] have changed
 *
 * HACK: This exists due to Android's complexities of scheduling an event at a given time.
 * It would be preferred to receive an event at the instant of rollover.
 *
 * NOTE: this may not be registered in the manifest as it listens for [ACTION_TIME_TICK].
 *
 * @see DayRolloverAlarm for a Manifest-registered alternative (using AlarmManager.setWindow - less
 * precise).
 */
object DayRolloverHandler : AnkiBroadcastReceiver() {
    /** @see Scheduler.dayCutoff */
    private var lastCutoff: EpochSeconds? = null

    /** Receive an event each minute AND for time/timezone changes */
    fun listenForRolloverEvents(context: Context) {
        fun register(filter: IntentFilter) = ContextCompat.registerReceiver(context, DayRolloverHandler, filter, RECEIVER_EXPORTED)

        Timber.d("listening for rollover events")
        // ACTION_TIME_TICK occurs every time the displayed time changes (once per minute)
        // this relies on the assumption that the day rollover's granularity is per-minute
        register(IntentFilter(ACTION_TIME_TICK))
        register(IntentFilter(ACTION_TIME_CHANGED))
        register(IntentFilter(ACTION_TIMEZONE_CHANGED))
    }

    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        // potential race condition if a timezone/tick change occur simultaneously
        // the outcome would be two calls to notifySubscribers, which is acceptable
        Timber.v("received ${intent.action}")
        // launch coroutine as we need access to `col.sched`
        applicationScope.launchCatching(Dispatchers.IO, errorMessageHandler = { msg ->
            CrashReportService.sendExceptionReport(
                e = ManuallyReportedException(msg),
                origin = "DayRolloverHandler::onReceive",
                onlyIfSilent = true,
            )
        }) {
            handleTimeChange()
        }
    }

    private suspend fun handleTimeChange() {
        val currentCutoff = withOpenColOrNull { sched.dayCutoff }
        if (currentCutoff == null) {
            // assumption: if the collection is not open, queue status will be updated
            // by the act of opening the collection, missing an event is acceptable
            Timber.w("could not check/update day rollover: collection not open")
            return
        }

        // Anki Desktop: instead of comparing the current time to the cutoff,
        // it detects if a change to the cutoff has occurred
        // https://github.com/ankitects/anki/blob/edd38ca06730d7fc16804f52ce10f6bc54c3d145/qt/aqt/main.py#L508-L528
        if (lastCutoff == currentCutoff) return

        Timber.i("day cutoff changed %d -> %d", lastCutoff, currentCutoff)
        // Re-arm the wall-clock alarm whenever the cutoff changes
        DayRolloverAlarm.scheduleNext(appContext)
        // we do not want to send a "study queues changes" message initially
        if (lastCutoff != null) {
            handleDayRollover()
        }
        this.lastCutoff = currentCutoff
    }

    private fun handleDayRollover() {
        Timber.i("day rollover occurred")

        Timber.i("updating study queues")
        ChangeManager.notifySubscribers(opChanges { studyQueues = true }, initiator = null)

        Timber.i("day rollover: updating widgets")
        try {
            WidgetStatus.updateInBackground(appContext)
        } catch (e: Exception) {
            Timber.w(e, "failed to update widgets")
        }
    }
}
