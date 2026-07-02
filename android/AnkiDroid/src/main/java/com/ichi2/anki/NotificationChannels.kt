/*
 * Copyright (c) 2018 Timothy Rae <perceptualchaos2@gmail.com>
 * Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.app.NotificationChannel
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import timber.log.Timber

/**
 * Minimum delay between notifications to avoid reaching the
 * maximum update rate, which currently is 5 updates per second.
 * https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/core/java/android/app/NotificationManager.java;l=675;drc=e13ace5dabea6d65c05dbfd9d19dc697a687d7be
 */
const val NOTIFICATION_MIN_DELAY_MS = 200L

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

    for (channel in Channel.entries) {
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
 * @property importance The [importance][NotificationChannel.getImportance] of the channel.
 */
enum class Channel(
    val id: String,
    @StringRes val nameId: Int,
    val importance: Int,
) {
    GENERAL("General Notifications", R.string.app_name, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    SYNC("Synchronization", R.string.sync_title, NotificationManagerCompat.IMPORTANCE_LOW),
    REVIEW_REMINDERS("Review Reminders", R.string.review_reminders_do_not_translate, NotificationManagerCompat.IMPORTANCE_DEFAULT),
    ;

    fun getName(res: Resources) = res.getString(nameId)
}
