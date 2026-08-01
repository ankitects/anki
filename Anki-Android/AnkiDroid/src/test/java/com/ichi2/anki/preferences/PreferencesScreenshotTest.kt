// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.preferences

import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.CollectionHelper
import com.ichi2.anki.R
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.settings.Prefs
import org.junit.Test

class PreferencesScreenshotTest : ScreenshotTest() {
    init {
        Prefs.isNewStudyScreenEnabled = true
    }

    @Test
    fun `capture all preference fragments`() {
        val fragments = PreferenceTestUtils.getAllPreferencesFragments(targetContext)

        fragments.forEach { fragment ->
            val fragmentClass = fragment::class
            ActivityScenario
                .launch<PreferencesActivity>(
                    PreferencesActivity.getIntent(targetContext, fragmentClass),
                ).use { scenario ->
                    scenario.onActivity { activity ->
                        val mainFragment = activity!!.fragment as PreferencesFragment
                        val settingsFragment = mainFragment.childFragmentManager.findFragmentById(R.id.settings_container)
                        // Robolectric generates a different temporary path every time,
                        // so avoid creating an unnecessary diff in the collection path pref summary
                        (settingsFragment as? AdvancedSettingsFragment)?.apply {
                            requirePreference<Preference>(CollectionHelper.PREF_COLLECTION_PATH).summaryProvider = {
                                "/storage/emulated/0/AnkiDroid"
                            }
                        }
                        (settingsFragment as? AboutFragment)?.apply {
                            binding.buildDate.text = "May 18, 2026"
                            binding.version.text = "2.24.0-screenshot"
                        }
                        captureScreen(fragmentClass.simpleName!!)
                    }
                }
        }
    }
}
