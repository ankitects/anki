// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.widget

import com.ichi2.anki.common.annotations.LegacyNotifications

/** The active [WidgetNotificationScheduler], set during app startup via [WidgetNotificationScheduler.register]. */
@LegacyNotifications("delete with the rest of the widget's legacy notification code")
lateinit var widgetNotificationScheduler: WidgetNotificationScheduler
    private set

/**
 * Triggers the legacy 'due cards' notification check, keeping widget code decoupled from
 * [com.ichi2.anki.AnkiDroidApp].
 * This moves with the widgets once they become a separate module.
 */
@LegacyNotifications("Only used by the widget to trigger notifications, we plan to stop relying on the widget")
fun interface WidgetNotificationScheduler {
    fun scheduleNotification()

    companion object {
        /** Use during app startup to set the global [WidgetNotificationScheduler] instance. */
        fun register(scheduler: WidgetNotificationScheduler) {
            widgetNotificationScheduler = scheduler
        }
    }
}
