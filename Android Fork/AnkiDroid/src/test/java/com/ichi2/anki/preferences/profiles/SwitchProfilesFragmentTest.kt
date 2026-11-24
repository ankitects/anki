// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.preferences.profiles

import androidx.preference.Preference
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.preferences.HeaderFragment
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.preferences.requirePreference
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SwitchProfilesFragmentTest : RobolectricTest() {
    @Test
    fun `screen goes back with a message when profiles failed to load`() {
        val activity = openSwitchProfilesWhenProfilesFailedToLoad()

        assertIs<HeaderFragment>(activity.settings.findFragmentById(R.id.settings_container))
        assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
    }

    @Test
    @Config(qualifiers = "w800dp-h1280dp")
    fun `split settings close with a message when profiles failed to load`() {
        val activity = openSwitchProfilesWhenProfilesFailedToLoad()

        assertTrue(activity.isFinishing)
        assertEquals(targetContext.getString(CommonString.something_wrong), ShadowToast.getTextOfLatestToast())
    }

    private fun openSwitchProfilesWhenProfilesFailedToLoad(): PreferencesActivity {
        val app = AnkiDroidApp.instance
        val profileManager = app.profileManager
        app.profileManager = null
        try {
            val activity = startRegularActivity<PreferencesActivity>(PreferencesActivity.getIntent(targetContext))
            activity.settings.fragments
                .filterIsInstance<HeaderFragment>()
                .single()
                .requirePreference<Preference>(R.string.pref_switch_profile_screen_key)
                .performClick()
            advanceRobolectricLooper()
            return activity
        } finally {
            app.profileManager = profileManager
        }
    }

    private val PreferencesActivity.settings
        get() = (fragment as PreferencesFragment).childFragmentManager
}
