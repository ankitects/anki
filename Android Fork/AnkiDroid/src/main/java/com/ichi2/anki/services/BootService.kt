// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2016 Siarhei Krukau <siarhei.krukau@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2025 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki.services

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import com.ichi2.anki.CollectionManager
import com.ichi2.anki.CommonPreferenceKey
import com.ichi2.anki.CommonString
import com.ichi2.anki.common.android.AnkiBroadcastReceiver
import com.ichi2.anki.common.annotations.LegacyNotifications
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.grantedStoragePermissions
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.common.utils.android.showThemedToast
import com.ichi2.anki.common.utils.ext.rolloverHour
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.preferences.PENDING_NOTIFICATIONS_ONLY
import com.ichi2.anki.runGloballyWithTimeout
import com.ichi2.anki.scheduleAllNotifications
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.widget.RECURRING_WIDGETS
import com.ichi2.widget.DayRolloverAlarm
import com.ichi2.widget.restoreRecurringAlarms
import timber.log.Timber
import java.util.Calendar
import kotlin.time.Duration.Companion.seconds

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
        // In the future, if more notifications are added to AnkiDroid, scheduleAllNotifications
        // should be extended to handle them, but since for now the only modernized notifications are review reminder
        // notifications, we block this behind the review-reminder-specific feature flag.
        if (Prefs.newReviewRemindersEnabled) {
            Timber.i("Setting notifications upon boot")
            runGloballyWithTimeout(SCHEDULE_NOTIFICATIONS_TIMEOUT) {
                scheduleAllNotifications(context)
            }
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

        restoreRecurringAlarms(context, RECURRING_WIDGETS)
        DayRolloverAlarm.scheduleNext(context)
        wasRun = true
    }

    @LegacyNotifications("Will be moved to AlarmManagement")
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
            error = CommonString.boot_service_too_many_notifications
        } catch (e: Exception) {
            Timber.w(e)
            error = CommonString.boot_service_failed_to_schedule_notifications
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
         * Timeout for the process of scheduling all AnkiDroid notifications.
         * Should be below 10 seconds as BroadcastReceivers may ANR when onReceive takes longer than 10 seconds.
         * See [the docs](https://developer.android.com/reference/android/content/BroadcastReceiver#goAsync()).
         */
        private val SCHEDULE_NOTIFICATIONS_TIMEOUT = 8.seconds

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
                        context.getString(CommonPreferenceKey.pref_notifications_minimum_cards_due_key),
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
                    2 -> col.config.rolloverHour
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
