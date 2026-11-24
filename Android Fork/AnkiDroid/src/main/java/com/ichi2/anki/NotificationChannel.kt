// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Timothy Rae <perceptualchaos2@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/**
 * Minimum delay between notifications to avoid reaching the
 * maximum update rate, which currently is 5 updates per second.
 * https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/app/NotificationManager.java;l=675;drc=e13ace5dabea6d65c05dbfd9d19dc697a687d7be
 */
val NOTIFICATION_MIN_DELAY = 200.milliseconds

/**
 * Create or update all the notification channels for the app
 *
 * In Oreo and higher, you must create a channel for all notifications.
 * This will create the channel if it doesn't exist, or if it exists it will update the name.
 *
 * Note that once a channel is created, only the name may be changed as long as the application
 * is installed on the user device. All other settings are fully under user control.
 *
 * TODO should be called in response to [Intent.ACTION_LOCALE_CHANGED]
 * @param context the context for access to localized strings for channel names
 */
fun setupNotificationChannels(context: Context) {
    val res = context.resources
    val manager = NotificationManagerCompat.from(context)

    for (channel in NotificationChannel.entries) {
        val id = channel.id
        val name = channel.getName(res)
        Timber.i("Creating notification channel with id/name: %s/%s", id, name)

        // Vibration is enabled by default, but the user can turn it off from the system settings
        // Vibration will also not occur if the phone has been set to silent
        val notificationChannel =
            NotificationChannelCompat
                .Builder(id, channel.importance)
                .setName(name)
                .setShowBadge(true)
                .setVibrationPattern(longArrayOf(0, 500))
                .setVibrationEnabled(true)
                .build()

        manager.createNotificationChannel(notificationChannel)
    }
}

/**
 * Defines the available notification channels for the application.
 *
 * @property id The unique identifier for the notification channel.
 * @property nameId The string resource ID for the localized channel name.
 * @property importance The [importance][android.app.NotificationChannel.getImportance] of the channel.
 */
enum class NotificationChannel(
    val id: String,
    @StringRes val nameId: Int,
    val importance: Int,
) {
    GENERAL("General Notifications", R.string.app_name, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    SYNC("Synchronization", CommonString.sync_title, NotificationManagerCompat.IMPORTANCE_LOW),
    REVIEW_REMINDERS("Review Reminders", CommonString.review_reminders_title, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    ;

    fun getName(res: Resources) = res.getString(nameId)
}
