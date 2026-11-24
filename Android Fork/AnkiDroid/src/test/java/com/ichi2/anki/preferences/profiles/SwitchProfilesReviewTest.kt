// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SwitchProfilesReviewTest {
    @Test
    fun `leaving the screen cannot abandon a committed profile switch`() {
        val mainScheduler = TestCoroutineScheduler()
        val ioScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
        ioDispatcher = StandardTestDispatcher(ioScheduler)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = context.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
        prefs.edit(commit = true) { clear() }
        val store = ViewModelStore()
        try {
            val manager = ProfileManager.create(context)
            val name = (ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name
            val target = manager.createNewProfile(name)
            var restarts = 0
            val model = SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) { restarts++ }
            store.put("profiles", model)
            model.requestSwitch(model.profiles.value.single { it.id == target })
            model.switchProfile()

            mainScheduler.runCurrent() // Dispatch the guard to IO.
            ioScheduler.runCurrent() // Commit the new profile; queue the continuation on Main.
            assertEquals(target.value, prefs.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null))
            assertEquals(0, restarts)

            store.clear() // Back navigation destroys the Fragment and clears its ViewModelStore.
            mainScheduler.runCurrent()
            ioScheduler.runCurrent()

            val persisted = prefs.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null)
            assertEquals(ProfileId.DEFAULT, manager.activeProfileContext.profileId)
            assertTrue(
                restarts == 1 || persisted == ProfileId.DEFAULT.value,
                "Switch abandoned: persisted=$persisted, running=${manager.activeProfileContext.profileId}, restarts=$restarts",
            )
        } finally {
            store.clear()
            prefs.edit(commit = true) { clear() }
            Dispatchers.resetMain()
            ioDispatcher = Dispatchers.IO
        }
    }
}
