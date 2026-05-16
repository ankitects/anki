// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.preferences

import androidx.test.core.app.ActivityScenario
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
                    scenario.onActivity {
                        captureScreen(fragmentClass.simpleName!!)
                    }
                }
        }
    }
}
