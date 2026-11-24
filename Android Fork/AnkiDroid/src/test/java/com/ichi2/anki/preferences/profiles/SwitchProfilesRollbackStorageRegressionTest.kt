// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import android.content.ContextWrapper
import androidx.core.content.edit
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.storage.CollectionHelper.PREF_COLLECTION_PATH
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SwitchProfilesRollbackStorageRegressionTest : RobolectricTest() {
    @Test
    fun `restart failure restores the running profile even when its storage is unavailable`() =
        runTest {
            val app = AnkiDroidApp.instance
            val originalManager = requireNotNull(app.profileManager)

            fun name(value: String) = (ProfileName.validate(value) as ProfileName.ValidationResult.Valid).name
            val work = originalManager.createNewProfile(name("Work"))
            ProfileSwitchGuard(originalManager, emptyList())(work)
            app.profileManager =
                ProfileManager.create(
                    object : ContextWrapper(app.baseContext) {
                        override fun getApplicationContext(): Context? = null
                    },
                )
            try {
                val manager = requireNotNull(app.profileManager)
                assertEquals(work, manager.activeProfileContext.profileId)
                val target = manager.createNewProfile(name("Personal"))
                val obstruction = File(tempFolder.root, "unavailable-running-collection").apply { writeText("not a directory") }
                manager.activeProfileContext.sharedPrefs().edit(commit = true) { putString(PREF_COLLECTION_PATH, obstruction.absolutePath) }
                val model = SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) { error("cannot start activities") }

                model.requestSwitch(model.profiles.value.single { it.id == target })
                model.switchProfile()
                advanceUntilIdle()

                val registry = app.baseContext.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                assertEquals(target, model.profileSwitchFailed.value?.id)
                assertEquals(
                    work.value,
                    registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null),
                    "A failed restart must restore the running profile without preparing its storage again",
                )
            } finally {
                app.profileManager = originalManager
            }
        }
}
