// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.isLoggedIn
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.settings.Prefs
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SwitchProfilesIsolationReviewTest : RobolectricTest() {
    private val app get() = AnkiDroidApp.instance

    // Application.attachBaseContext receives the framework context before applicationContext is set.
    private fun startupContext() =
        object : ContextWrapper(app.baseContext) {
            override fun getApplicationContext(): Context? = null
        }

    private fun createProfile(manager: ProfileManager) =
        manager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)

    @Test
    fun `a healthy target profile does not expose Defaults sync account`() =
        runTest {
            val originalManager = requireNotNull(app.profileManager)
            Prefs.hkey = "outgoing-default-account-key"
            val target = createProfile(originalManager)
            var accountVisibleAfterRestart: String? = "restart not reached"
            try {
                val model =
                    SwitchProfilesViewModel(originalManager, ProfileSwitchGuard(originalManager, emptyList())) {
                        app.profileManager = ProfileManager.createOrNull(startupContext())
                        accountVisibleAfterRestart = Prefs.hkey
                    }
                model.requestSwitch(model.profiles.value.single { it.id == target })
                model.switchProfile()
                advanceUntilIdle()

                assertEquals(target, assertNotNull(app.profileManager).activeProfileContext.profileId)
                assertNull(accountVisibleAfterRestart)
            } finally {
                app.profileManager = originalManager
                app.sharedPrefs().edit(commit = true) { remove("hkey") }
                ProfileManager.resetForTesting()
            }
        }

    @Test
    fun `a target profile that cannot load is refused before the restart`() =
        runTest {
            val originalManager = requireNotNull(app.profileManager)
            Prefs.hkey = "outgoing-default-account-key"
            Prefs.username = "default@example.invalid"
            assertTrue(isLoggedIn())
            val target = createProfile(originalManager)
            val root = File(app.baseContext.filesDir.parentFile, target.value)
            assertFalse(root.exists())
            root.writeText("not a directory")
            var restarted = false
            try {
                val model =
                    SwitchProfilesViewModel(originalManager, ProfileSwitchGuard(originalManager, emptyList())) {
                        restarted = true
                    }
                model.requestSwitch(model.profiles.value.single { it.id == target })
                model.switchProfile()
                advanceUntilIdle()

                val registry = app.baseContext.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                assertEquals(
                    ProfileId.DEFAULT.value,
                    registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null),
                    "Work could not be opened, so Default must stay selected",
                )
                assertFalse(restarted, "a restart would fall back to Default under Work's name")
                assertEquals(target, model.profileSwitchFailed.value?.id)
                assertSame(originalManager, app.profileManager)
            } finally {
                app.profileManager = originalManager
                root.delete()
                app.sharedPrefs().edit(commit = true) {
                    remove("hkey")
                    remove("username")
                }
                ProfileManager.resetForTesting()
            }
        }
}
