// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences

import androidx.fragment.app.Fragment
import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.ichi2.anki.preferences.profiles.SwitchProfilesFragment
import com.ichi2.anki.preferences.reviewer.ReviewerMenuSettingsFragment
import com.ichi2.anki.reviewreminders.ScheduleRemindersFragment
import kotlin.reflect.KClass

/**
 * Supplies every preference fragment as a [com.google.testing.junit.testparameterinjector.TestParameter],
 * so a test runs once per fragment.
 *
 * Values are provided before Robolectric starts, so the fragments can't be read from XML:
 * [PreferencesTest] checks this list matches [PreferenceTestUtils.getAllPreferencesFragments].
 */
class PreferenceFragmentsProvider : TestParameterValuesProvider() {
    override fun provideValues(context: Context?): List<*> = FRAGMENTS.map { value(it).withName(it.simpleName!!) }

    companion object {
        val FRAGMENTS: List<KClass<out Fragment>> =
            listOf(
                HeaderFragment::class,
                GeneralSettingsFragment::class,
                ReviewingSettingsFragment::class,
                TimeboxSettingsFragment::class,
                SyncSettingsFragment::class,
                CustomSyncServerSettingsFragment::class,
                NotificationsSettingsFragment::class,
                ScheduleRemindersFragment::class,
                AppearanceSettingsFragment::class,
                CustomButtonsSettingsFragment::class,
                ControlsSettingsFragment::class,
                AccessibilitySettingsFragment::class,
                ReviewerOptionsFragment::class,
                ReviewerMenuSettingsFragment::class,
                AnswerButtonsSettingsFragment::class,
                BackupLimitsSettingsFragment::class,
                AdvancedSettingsFragment::class,
                SwitchProfilesFragment::class,
                DeveloperOptionsFragment::class,
                AboutFragment::class,
            )
    }
}
