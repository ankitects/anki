// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki

import android.app.Activity
import android.content.Context
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PREF_LAST_ACTIVE_PROFILE_ID
import com.ichi2.anki.multiprofile.ProfileManager.Companion.PROFILE_REGISTRY_FILENAME
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(application = ProfileActivityContextTest.NonDefaultProfileApp::class)
class ProfileActivityContextTest {
    @After
    fun tearDown() {
        ProfileManager.resetForTesting()
    }

    @Test
    fun `AnkiActivity storage follows the profile`() {
        assertStorageInProfile(Robolectric.buildActivity(AnkiActivity::class.java).get())
    }

    @Test
    fun `IntentHandler storage follows the profile`() {
        assertStorageInProfile(Robolectric.buildActivity(IntentHandler2::class.java).get())
    }

    private fun assertStorageInProfile(activity: Activity) {
        activity.getSharedPreferences("settings", Context.MODE_PRIVATE).edit(commit = true) { putString("key", "value") }
        activity.sharedPrefs().edit(commit = true) { putString("key", "value") }

        val sharedPrefsDir = File(activity.dataDir, "shared_prefs")
        assertTrue(File(sharedPrefsDir, "profile_${PROFILE}_settings.xml").exists())
        assertTrue(File(sharedPrefsDir, "profile_${PROFILE}_${activity.packageName}_preferences.xml").exists())
        assertTrue("${activity.filesDir}", activity.filesDir.startsWith(File(activity.dataDir, PROFILE)))
    }

    class NonDefaultProfileApp : AnkiDroidApp() {
        override fun attachBaseContext(base: Context) {
            base.getSharedPreferences(PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE).edit(commit = true) {
                putString(PREF_LAST_ACTIVE_PROFILE_ID, PROFILE)
            }
            super.attachBaseContext(base)
        }
    }

    companion object {
        private const val PROFILE = "p_activity"
    }
}
