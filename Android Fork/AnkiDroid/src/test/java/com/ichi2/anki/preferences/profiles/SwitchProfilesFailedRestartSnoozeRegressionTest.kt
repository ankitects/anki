// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.safeRestartApp
import com.ichi2.anki.reviewreminders.ReviewReminder
import com.ichi2.anki.reviewreminders.ReviewReminderAlarmManager
import com.ichi2.anki.reviewreminders.ReviewReminderTime
import com.ichi2.anki.reviewreminders.ReviewRemindersDatabase
import com.ichi2.anki.services.NotificationService
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class SwitchProfilesFailedRestartSnoozeRegressionTest : RobolectricTest() {
    @Test
    fun `a failed restart retains the pending snooze at its original time`() =
        runTest { assertPendingSnoozeSurvives(AnkiDroidApp.instance) }

    @Test
    fun `a failed restart retains receiver-scheduled snoozes in a non-default profile`() =
        runTest {
            val app = AnkiDroidApp.instance
            val originalManager = requireNotNull(app.profileManager)
            val work = originalManager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            ProfileSwitchGuard(originalManager, emptyList())(work)
            app.profileManager =
                ProfileManager.create(
                    object : ContextWrapper(app.baseContext) {
                        override fun getApplicationContext(): Context? = null
                    },
                )
            try {
                assertEquals(work, app.profileManager!!.activeProfileContext.profileId)
                // Framework receivers receive a base context rather than the profile-wrapped application.
                assertPendingSnoozeSurvives(app.baseContext)
            } finally {
                app.profileManager = originalManager
            }
        }

    private suspend fun assertPendingSnoozeSurvives(schedulingContext: Context) {
        val app = AnkiDroidApp.instance
        val reminder = ReviewReminder.createReviewReminder(time = ReviewReminderTime(20, 0))
        ReviewRemindersDatabase.insertReminder(reminder)
        ReviewReminderAlarmManager.scheduleReviewReminderNotification(app, reminder, attemptImmediateNotification = false)
        ReviewReminderAlarmManager.scheduleSnoozedNotification(schedulingContext, reminder, snoozeIntervalInMinutes = 5)
        val alarms = shadowOf(app.getSystemService(AlarmManager::class.java))

        @Suppress("DEPRECATION")
        fun snoozeTimes() =
            alarms.scheduledAlarms
                .filter {
                    shadowOf(it.operation).savedIntent.action ==
                        NotificationService.NotificationServiceAction.SnoozeNotification.actionString
                }.map { it.triggerAtTime }

        val expectedTimes = snoozeTimes()
        assertEquals(1, expectedTimes.size)
        val refusesActivities =
            object : ContextWrapper(app) {
                override fun startActivity(intent: Intent) = throw IllegalStateException("cannot start activities")
            }
        assertFailsWith<IllegalStateException> { safeRestartApp(refusesActivities) }

        assertEquals(expectedTimes, snoozeTimes(), "Remaining in the original profile must preserve its pending snooze")
    }
}
