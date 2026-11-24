// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings

import androidx.core.content.edit
import com.github.ivanshafran.sharedpreferencesmock.SPMockBuilder
import com.ichi2.anki.CommonPreferenceKey
import com.ichi2.testutils.inMemoryPrefs
import org.junit.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class NotificationPreferencesTest {
    private val preferences = SPMockBuilder().createSharedPreferences()
    private val notifications = inMemoryPrefs(preferences)

    private val newReviewRemindersKey = notifications.key(CommonPreferenceKey.pref_new_review_reminders)
    private val minimumCardsDueKey = notifications.key(CommonPreferenceKey.pref_notifications_minimum_cards_due_key)

    @Test
    fun `existing reminder preference is read and updated through its key`() {
        preferences.edit { putBoolean(newReviewRemindersKey, true) }
        assertTrue(notifications.newReviewRemindersEnabled)
        notifications.newReviewRemindersEnabled = false
        assertFalse(preferences.getBoolean(newReviewRemindersKey, true))
    }

    @Test
    fun `both notification systems default to disabled`() {
        assertFalse(notifications.newReviewRemindersEnabled)
        assertFalse(notifications.legacyNotificationsEnabled)
    }

    @Test
    fun `legacy notification threshold retains its disabled boundary`() {
        preferences.edit { putString(minimumCardsDueKey, "999999") }
        assertTrue(notifications.legacyNotificationsEnabled)
        preferences.edit { putString(minimumCardsDueKey, "1000000") }
        assertFalse(notifications.legacyNotificationsEnabled)
    }
}
