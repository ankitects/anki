// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2022 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki.preferences

import android.app.AlarmManager
import android.content.Context.ALARM_SERVICE
import android.content.Intent
import androidx.core.app.PendingIntentCompat
import androidx.preference.ListPreference
import androidx.preference.SwitchPreferenceCompat
import com.ichi2.anki.R
import com.ichi2.anki.common.android.AdaptionUtil
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.services.BootService.Companion.scheduleNotification
import com.ichi2.anki.services.NotificationService

/**
 * Fragment with preferences related to notifications
 */
class NotificationsSettingsFragment : SettingsFragment() {
    override val preferenceResource: Int
        get() = R.xml.preferences_notifications
    override val analyticsScreenNameConstant: String
        get() = "prefs.notifications"

    override fun initSubscreen() {
        if (AdaptionUtil.isXiaomiRestrictedLearningDevice) {
            /* These preferences should be searchable or not based
             * on this same condition at [HeaderFragment.configureSearchBar] */
            preferenceScreen.removePreference(requirePreference<SwitchPreferenceCompat>(R.string.pref_notifications_vibrate_key))
            preferenceScreen.removePreference(requirePreference<SwitchPreferenceCompat>(R.string.pref_notifications_blink_key))
        }
        // Minimum cards due
        // The number of cards that should be due today in a deck to justify adding a notification.
        requirePreference<ListPreference>(R.string.pref_notifications_minimum_cards_due_key).apply {
            updateNotificationPreference(this)
            setOnPreferenceChangeListener { preference, newValue ->
                updateNotificationPreference(preference as ListPreference)
                if ((newValue as String).toInt() < PENDING_NOTIFICATIONS_ONLY) {
                    scheduleNotification(TimeManager.time, requireContext())
                } else {
                    val intent =
                        PendingIntentCompat.getBroadcast(
                            requireContext(),
                            0,
                            Intent(requireContext(), NotificationService::class.java),
                            0,
                            false,
                        )
                    val alarmManager = requireActivity().getSystemService(ALARM_SERVICE) as AlarmManager
                    if (intent != null) {
                        alarmManager.cancel(intent)
                    }
                }
                true
            }
        }
    }

    private fun updateNotificationPreference(listPreference: ListPreference) {
        val entries = listPreference.entries
        val values = listPreference.entryValues
        for (i in entries.indices) {
            val value = values[i].toString().toInt()
            if (entries[i].toString().contains("%d")) {
                entries[i] = String.format(entries[i].toString(), value)
            }
        }
        listPreference.entries = entries
    }
}
