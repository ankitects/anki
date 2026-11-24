// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.preference.SwitchPreferenceCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.preferences.DeveloperOptionsFragment
import com.ichi2.anki.preferences.PreferencesActivity
import com.ichi2.anki.preferences.PreferencesFragment
import com.ichi2.anki.preferences.requirePreference
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SwitchProfilesRoundTripReviewTest : RobolectricTest() {
    @Test
    fun `failed profile persistence must not restart the app`() {
        val dispatcher = UnconfinedTestDispatcher()
        Dispatchers.setMain(dispatcher)
        ioDispatcher = dispatcher
        val base = AnkiDroidApp.instance.baseContext
        val prefs = base.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
        var rejectCommits = false
        var rejectedCommits = 0
        val failurePrefs =
            object : SharedPreferences by prefs {
                override fun edit(): SharedPreferences.Editor {
                    val editor = prefs.edit()
                    return object : SharedPreferences.Editor by editor {
                        override fun commit(): Boolean {
                            if (!rejectCommits) return editor.commit()
                            rejectedCommits++
                            return false
                        }
                    }
                }
            }
        val context =
            object : ContextWrapper(base) {
                override fun getApplicationContext(): Context? = null

                override fun getSharedPreferences(
                    name: String,
                    mode: Int,
                ): SharedPreferences =
                    if (name == ProfileManager.PROFILE_REGISTRY_FILENAME) failurePrefs else super.getSharedPreferences(name, mode)
            }
        val manager = ProfileManager.create(context)
        val name = (ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name
        val target = manager.createNewProfile(name)
        var restarts = 0
        val model = SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) { restarts++ }
        rejectCommits = true

        model.requestSwitch(model.profiles.value.single { it.id == target })
        model.switchProfile()

        assertEquals(1, rejectedCommits, "the persistence operation must have failed")
        assertEquals(0, restarts, "restart was requested even though commit returned false")
    }

    @Test
    fun `switch profile option can be enabled from a non-default profile`() {
        val app = AnkiDroidApp.instance
        val originalManager = app.profileManager
        val base = app.baseContext
        val flag = app.getString(R.string.pref_enable_switch_profile_key)
        base.sharedPrefs().edit(commit = true) { putBoolean(flag, true) }
        val manager = requireNotNull(originalManager)
        val name = (ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name
        val target = manager.createNewProfile(name)
        runBlocking { ProfileSwitchGuard(manager, emptyList())(target) }

        // Reload using the framework base as Application.attachBaseContext does after a restart.
        app.profileManager =
            ProfileManager.create(
                object : ContextWrapper(base) {
                    override fun getApplicationContext(): Context? = null
                },
            )
        try {
            assertFalse(Prefs.switchProfileEnabled, "new profile starts with this developer flag off")
            val activity =
                startRegularActivity<PreferencesActivity>(
                    PreferencesActivity.getIntent(targetContext, DeveloperOptionsFragment::class),
                )
            val settings = (activity.fragment as PreferencesFragment).childFragmentManager
            val developerOptions = settings.fragments.filterIsInstance<DeveloperOptionsFragment>().single()
            val toggle = developerOptions.requirePreference<SwitchPreferenceCompat>(R.string.pref_enable_switch_profile_key)

            // Switching the preference off and on follows TwoStatePreference's real persistence path.
            toggle.isChecked = false
            toggle.isChecked = true

            assertTrue(
                Prefs.switchProfileEnabled,
                "Toggle=${toggle.isChecked}, Default=${base.sharedPrefs().getBoolean(flag, false)}, " +
                    "active profile=${app.sharedPrefs().getBoolean(flag, false)}; the profile list remains hidden",
            )
        } finally {
            app.profileManager = originalManager
        }
    }
}
