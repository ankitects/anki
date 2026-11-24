// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Eric Li <ericli3690@gmail.com>

package com.ichi2.anki

import android.content.Context
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager

/**
 * Schedules all notifications defined by AnkiDroid.
 * Since notifications are deleted when the device restarts, this method should be called on
 * device start-up, on app start-up, etc.
 * To extend the notifications created by AnkiDroid, add more functionality to the body of this method.
 */
suspend fun scheduleAllNotifications(context: Context) {
    // currently, the only scheduled notifications supported by AnkiDroid are review reminder notifications
    ReviewReminderAlarmManager.scheduleAllEnabledReviewReminderNotifications(context)
}
