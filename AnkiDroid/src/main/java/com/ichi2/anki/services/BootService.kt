/*
 *  Copyright (c) 2016 Siarhei Krukau <siarhei.krukau@gmail.com>
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

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.IntentHandler.Companion.grantedStoragePermissions
import com.ichi2.anki.R
import com.ichi2.anki.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.preferences.PENDING_NOTIFICATIONS_ONLY
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.anki.settings.Prefs
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.restoreRecurringAlarms
import timber.log.Timber
import java.util.Calendar

/**
 * BroadcastReceiver which listens to the Android system-level intent that fires when the device starts up.
 * Schedules notifications for review reminders.
 *
 * Note that Android battery optimizations may potentially block us from receiving the [Intent.ACTION_BOOT_COMPLETED]
 * intent, which could cause review reminders to not be scheduled.
 */
@NeedsTest("Check on various Android versions that this can execute")
class BootService : AnkiBroadcastReceiver() {
    @LegacyNotifications("Notifications will be scheduled rather than instantly shown on boot or app launch")
    private var failedToShowNotifications = false

    override fun onReceiveBroadcast(
        context: Context,
        intent: Intent,
    ) {
        if (!intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            Timber.w("BootService - unexpected action received, ignoring: %s", intent.action)
            return
        }
        if (wasRun) {
            Timber.d("BootService - Already run")
            return
        }
        if (runCatching { grantedStoragePermissions(context, showToast = false) }.getOrNull() != true) {
            Timber.w("Boot Service did not execute - no permissions")
            return
        }
        if (Prefs.newReviewRemindersEnabled) {
            Timber.i("Executing Boot Service - Review reminders")
            AlarmManagerService.scheduleAllNotifications(context)
        } else {
            // There are cases where the app is installed, and we have access, but nothing exist yet
            val col = getColSafe()
            if (col == null) {
                Timber.w("Boot Service did not execute - error loading collection")
                return
            }
            Timber.i("Executing Boot Service")
            catchAlarmManagerErrors(context) { scheduleNotification(TimeManager.time, context) }
            failedToShowNotifications = false
        }

        restoreRecurringAlarms(context)
        DayRolloverAlarm.scheduleNext(context)
        wasRun = true
    }

    @LegacyNotifications("Will be moved to AlarmManagerService")
    private fun catchAlarmManagerErrors(
        context: Context,
        runnable: Runnable,
    ) {
        // #6332 - Too Many Alarms on Samsung Devices - this stops a fatal startup crash.
        // We warn the user if they breach this limit
        var error: Int? = null
        try {
            runnable.run()
        } catch (ex: SecurityException) {
            Timber.w(ex)
            error = R.string.boot_service_too_many_notifications
        } catch (e: Exception) {
            Timber.w(e)
            error = R.string.boot_service_failed_to_schedule_notifications
        }
        if (error != null) {
            if (!failedToShowNotifications) {
                showThemedToast(context, context.getString(error), false)
            }
            failedToShowNotifications = true
        }
    }

    @LegacyNotifications("Can use withCol instead")
    private fun getColSafe(): Collection? {
        // #6239 - previously would crash if ejecting, we don't want a report if this happens so don't use
        // getInstance().getColSafe
        return try {
            CollectionManager.getColUnsafe()
        } catch (e: Throwable) {
            // Error and Exception paths are the same, so catch Throwable
            // BackendException.BackendFatalError is a RuntimeException
            // java.lang.UnsatisfiedLinkError occurs in tests
            Timber.e(e, "Failed to get collection for boot service - possibly media ejecting")
            null
        }
    }

    companion object {
        /**
         * This service is also run when the app is started (from [com.ichi2.anki.AnkiDroidApp],
         * so we need to make sure that it isn't run twice.
         */
        private var wasRun = false

        @LegacyNotifications("Replaced by new review reminder scheduling logic")
        fun scheduleNotification(
            time: Time,
            context: Context,
        ) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val sp = context.sharedPrefs()
            // Don't schedule a notification if the due reminders setting is not enabled
            if (sp
                    .getString(
                        context.getString(R.string.pref_notifications_minimum_cards_due_key),
                        PENDING_NOTIFICATIONS_ONLY.toString(),
                    )!!
                    .toInt() >= PENDING_NOTIFICATIONS_ONLY
            ) {
                return
            }
            val calendar = time.calendar()
            calendar.apply {
                set(Calendar.HOUR_OF_DAY, getRolloverHourOfDay(context))
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
            }
            val notificationIntent =
                PendingIntentCompat.getBroadcast(
                    context,
                    0,
                    Intent(context, NotificationService::class.java),
                    0,
                    false,
                )
            if (notificationIntent != null) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    notificationIntent,
                )
            }
        }

        /** Returns the hour of day when rollover to the next day occurs  */
        @LegacyNotifications("Notifications will be sent at customizable times rather than the beginning of every day")
        private fun getRolloverHourOfDay(context: Context): Int {
            // TODO; We might want to use the BootService retry code here when called from preferences.
            val defValue = 4
            return try {
                val col = CollectionManager.getColUnsafe()
                when (col.schedVer()) {
                    1 -> {
                        val sp = context.sharedPrefs()
                        sp.getInt("dayOffset", defValue)
                    }
                    2 -> col.config.get("rollover") ?: defValue
                    else -> {
                        val sp = context.sharedPrefs()
                        sp.getInt("dayOffset", defValue)
                    }
                }
            } catch (e: Exception) {
                Timber.w(e)
                defValue
            }
        }
    }
}
