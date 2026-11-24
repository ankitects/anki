// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewreminders

import android.app.NotificationManager
import android.os.Build
import android.view.View
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.ui.windows.permissions.PermissionsBottomSheet
import com.ichi2.testutils.positiveButton
import com.ichi2.testutils.rules.OverridePropertyRule
import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.nullValue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf

/** Tests AnkiDroid's permission sheet without changing the device's notification permission. */
@RunWith(AndroidJUnit4::class)
class ReviewReminderNotificationPermissionTest : RobolectricTest() {
    @get:Rule
    val notificationPreferences =
        OverridePropertyRule(
            Prefs::reminderNotifsRequestShown to false,
            Prefs::notificationsPermissionRequested to false,
            Prefs::notificationsBottomSheetShownBelowAPI33 to false,
        )

    @Before
    fun setUpReminders() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
        shadowOf(targetContext.getSystemService<NotificationManager>()!!).setNotificationsEnabled(false)
    }

    @After
    fun tearDownReminders() {
        ReviewRemindersDatabase.remindersSharedPrefs.edit { clear() }
    }

    @Test
    fun notificationPermissionPromptIsOnlyShownOnce() {
        withScheduleRemindersFragment { fragment ->
            fragment.createReminder(expectedCount = 1)

            val permissionViewId =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    R.id.notification_permission
                } else {
                    R.id.legacy_notification_permission
                }
            advanceRobolectricLooperUntil {
                fragment.permissionsBottomSheet
                    ?.view
                    ?.findViewById<View>(permissionViewId)
                    ?.isShown == true
            }
            assertThat(Prefs.reminderNotifsRequestShown, equalTo(true))
            fragment.permissionsBottomSheet!!
                .requireView()
                .findViewById<View>(R.id.close_button)
                .performClick()
            advanceRobolectricLooperUntil { fragment.permissionsBottomSheet == null }
            assertThat(fragment.reminderCount, equalTo(1))
            assertThat(storedReminders().size, equalTo(1))
        }
        // Only the reminder-specific flag should prevent the second prompt, including below API 33.
        Prefs.notificationsBottomSheetShownBelowAPI33 = false
        withScheduleRemindersFragment { fragment ->
            fragment.createReminder(expectedCount = 2)

            assertThat(fragment.permissionsBottomSheet, nullValue())
        }
    }

    @Test
    fun notificationPermissionPromptIsSkippedWhenGranted() {
        shadowOf(targetContext.getSystemService<NotificationManager>()!!).setNotificationsEnabled(true)
        withScheduleRemindersFragment { fragment ->
            fragment.createReminder(expectedCount = 1)

            assertThat(fragment.permissionsBottomSheet, nullValue())
            assertThat(
                "A sheet that was not shown must not consume the first request",
                Prefs.reminderNotifsRequestShown,
                equalTo(false),
            )
        }
    }

    @Test
    fun cancelCreatingReminderDoesNotRequestNotificationPermission() {
        withScheduleRemindersFragment { fragment ->
            fragment
                .openAddDialog()
                .requireDialog()
                .findViewById<View>(android.R.id.button3)
                .performClick()
            advanceRobolectricLooper()

            assertThat(fragment.permissionsBottomSheet, nullValue())
            assertThat(Prefs.reminderNotifsRequestShown, equalTo(false))
            assertThat(storedReminders(), empty())
            assertThat(fragment.reminderCount, equalTo(0))
        }
    }

    private fun ScheduleRemindersFragment.openAddDialog(): AddEditReminderDialog {
        binding.floatingActionButtonAdd.performClick()
        advanceRobolectricLooper()
        return childFragmentManager.fragments.filterIsInstance<AddEditReminderDialog>().single()
    }

    private fun ScheduleRemindersFragment.createReminder(expectedCount: Int) {
        openAddDialog().positiveButton.performClick()
        advanceRobolectricLooperUntil { storedReminders().size == expectedCount && reminderCount == expectedCount }
    }

    private fun storedReminders(): List<ReviewReminder> = runBlocking { ReviewRemindersDatabase.getAllReminders().getRemindersList() }

    private val ScheduleRemindersFragment.permissionsBottomSheet: PermissionsBottomSheet?
        get() = childFragmentManager.fragments.filterIsInstance<PermissionsBottomSheet>().singleOrNull()

    private val ScheduleRemindersFragment.reminderCount: Int
        get() = binding.recyclerView.adapter!!.itemCount
}
