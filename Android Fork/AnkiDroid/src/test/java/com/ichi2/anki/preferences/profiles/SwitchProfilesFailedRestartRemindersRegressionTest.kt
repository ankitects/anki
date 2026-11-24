// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.safeRestartApp
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.services.NotificationService
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SwitchProfilesFailedRestartRemindersRegressionTest : RobolectricTest() {
    @Test
    fun `a failed restart preserves recurring reminders for the restored profile`() =
        runTest {
            val app = AnkiDroidApp.instance
            val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
            ReviewRemindersDatabase.insertReminder(reminder)
            ReviewReminderAlarmManager.scheduleReviewReminderNotification(app, reminder, attemptImmediateNotification = false)
            val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

            @Suppress("DEPRECATION")
            fun recurringAlarmCount() =
                alarms.scheduledAlarms.count {
                    shadowOf(it.operation).savedIntent.action ==
                        NotificationService.NotificationServiceAction.ScheduleRecurringNotifications.actionString
                }
            assertEquals(1, recurringAlarmCount())
            val manager = requireNotNull(app.profileManager)
            val target = manager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            val refusesActivities =
                object : ContextWrapper(app) {
                    override fun startActivity(intent: Intent) = throw IllegalStateException("cannot start activities")
                }
            val model = SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) { safeRestartApp(refusesActivities) }

            model.requestSwitch(model.profiles.value.single { it.id == target })
            model.switchProfile()
            advanceUntilIdle()

            val registry = app.baseContext.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
            assertEquals(ProfileId.DEFAULT.value, registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null))
            assertEquals(target, model.profileSwitchFailed.value?.id)
            assertEquals(1, recurringAlarmCount(), "The app stayed in Default, so its reminder must still be scheduled")
        }
}
