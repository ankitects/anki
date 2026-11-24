// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class SwitchProfilesRollbackCommitFailureTest : RobolectricTest() {
    @Test
    fun `a failed rollback must not restore the rejected switch target`() =
        runTest {
            val base = AnkiDroidApp.instance.baseContext
            val registry = base.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
            var failNextCommit = false
            var failedCommits = 0
            val failingPrefs =
                object : SharedPreferences by registry {
                    override fun edit(): SharedPreferences.Editor {
                        val editor = registry.edit()
                        return object : SharedPreferences.Editor by editor {
                            override fun commit(): Boolean {
                                if (!failNextCommit) return editor.commit()
                                failNextCommit = false
                                failedCommits++
                                // Android updates its in-memory map even when committing to disk fails.
                                editor.apply()
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
                        if (name == ProfileManager.PROFILE_REGISTRY_FILENAME) failingPrefs else super.getSharedPreferences(name, mode)
                }
            val manager = ProfileManager.create(context)
            val target = manager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            val model =
                SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) {
                    assertEquals(target.value, registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null))
                    failNextCommit = true
                    error("Restart rejected")
                }

            model.requestSwitch(model.profiles.value.single { it.id == target })
            model.switchProfile()
            advanceUntilIdle()

            assertEquals(1, failedCommits)
            assertEquals(target, model.profileSwitchFailed.value?.id)
            assertFalse(manager.isSwitching)
            assertEquals(
                ProfileId.DEFAULT,
                ProfileManager.create(context).activeProfileContext.profileId,
                "A failed restart must keep the running profile selected even if rollback commit fails once",
            )
        }
}
