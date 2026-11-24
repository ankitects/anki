// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.preferences

/** Preferences shared by review reminders and the legacy widget notifications. */
interface NotificationPreferences {
    /** Whether to enable the new review reminders notification system. */
    val newReviewRemindersEnabled: Boolean

    /** Whether the legacy notification threshold allows due-card notifications. */
    val legacyNotificationsEnabled: Boolean
}
