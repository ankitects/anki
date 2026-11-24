// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.servicemodel

import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.Companion.PREF_PREFERENCE_UPGRADE_VERSION
import com.ichi2.anki.servicelayer.PreferenceUpgradeService.PreferenceUpgrade.UpgradeGesturesToControls
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import timber.log.Timber

@RunWith(AndroidJUnit4::class)
class UpgradeGesturesToControlsTestNoParam : RobolectricTest() {
    private val changedKeys = HashSet<String>()

    private lateinit var prefs: SharedPreferences
    private lateinit var instance: UpgradeGesturesToControls

    @Before
    override fun setUp() {
        super.setUp()
        prefs = super.getPreferences()
        instance = UpgradeGesturesToControls()
        prefs.registerOnSharedPreferenceChangeListener { _, key ->
            run {
                Timber.i("added key $key")
                if (key != null) {
                    changedKeys.add(key)
                }
            }
        }
    }

    @Test
    @Ignore("flaky in CI")
    fun test_preferences_not_opened_happy_path() {
        // if the user has not opened the gestures, then nothing should be mapped
        assertThat(prefs.contains(PREF_KEY_VOLUME_DOWN), equalTo(false))
        assertThat(prefs.contains(PREF_KEY_VOLUME_UP), equalTo(false))

        upgradeAllGestures()

        // ensure that no settings were added to the preferences
        assertThat(changedKeys, Matchers.contains(PREF_PREFERENCE_UPGRADE_VERSION))
    }

    @Test
    @Ignore("flaky in CI")
    fun test_preferences_opened_happy_path() {
        // the default is that the user has not mapped the gesture, but has opened the screen
        // so they are set to NOTHING which had "0" as value
        prefs.edit { putString(PREF_KEY_VOLUME_UP, "0") }
        prefs.edit { putString(PREF_KEY_VOLUME_DOWN, "0") }

        assertThat(prefs.contains(PREF_KEY_VOLUME_DOWN), equalTo(true))
        assertThat(prefs.contains(PREF_KEY_VOLUME_UP), equalTo(true))

        upgradeAllGestures()

        // ensure that no settings were added to the preferences
        assertThat(changedKeys, Matchers.contains(PREF_PREFERENCE_UPGRADE_VERSION, PREF_KEY_VOLUME_DOWN, PREF_KEY_VOLUME_UP))

        assertThat("Volume gestures are removed", prefs.contains(PREF_KEY_VOLUME_DOWN), equalTo(false))
        assertThat("Volume gestures are removed", prefs.contains(PREF_KEY_VOLUME_UP), equalTo(false))
    }

    private fun upgradeAllGestures() {
        changedKeys.clear()
        instance.performUpgrade(prefs)
    }

    companion object {
        const val PREF_KEY_VOLUME_UP = "gestureVolumeUp"
        const val PREF_KEY_VOLUME_DOWN = "gestureVolumeDown"
    }
}
