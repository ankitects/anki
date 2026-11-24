// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.backupLock
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import com.ichi2.anki.multiprofile.backupCheck
import com.ichi2.anki.multiprofile.mediaSyncCheck
import com.ichi2.anki.multiprofile.safeRestartApp
import com.ichi2.anki.multiprofile.syncCheck
import com.ichi2.anki.worker.mediaSyncLock
import com.ichi2.anki.worker.syncLock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertNull

@RunWith(AndroidJUnit4::class)
class SwitchProfilesThirdReviewTest : RobolectricTest() {
    private fun name(value: String) = (ProfileName.validate(value) as ProfileName.ValidationResult.Valid).name

    @Test
    fun `an older switch cannot overwrite the last confirmed profile`() {
        val mainScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
        val queuedIo = QueuedDispatcher()
        ioDispatcher = queuedIo
        val app = AnkiDroidApp.instance
        val manager = requireNotNull(app.profileManager)
        val work = manager.createNewProfile(name("Work"))
        val personal = manager.createNewProfile(name("Personal"))
        val registry = app.baseContext.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
        val store = ViewModelStore()
        var restarts = 0
        val model =
            SwitchProfilesViewModel(
                manager,
                ProfileSwitchGuard(manager, listOf(syncCheck(), mediaSyncCheck(), backupCheck())),
            ) {
                restarts++
                safeRestartApp(app)
            }
        store.put("profiles", model)
        try {
            model.requestSwitch(model.profiles.value.single { it.id == work })
            model.switchProfile()
            mainScheduler.runCurrent()

            model.requestSwitch(model.profiles.value.single { it.id == personal })
            assertNull(model.profileToSwitchTo.value, "Personal must not be offered while the switch to Work is saving")
            model.switchProfile()
            mainScheduler.runCurrent()
            assertEquals(1, queuedIo.tasks.size, "only the first confirmation was accepted")

            queuedIo.tasks.removeFirst().run()
            mainScheduler.runCurrent()

            assertEquals(
                work.value,
                registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null),
                "Work was confirmed and Personal was never offered; restarts entered=$restarts",
            )
            assertEquals(1, restarts)
        } finally {
            store.clear()
            mainScheduler.runCurrent()
            listOf(syncLock, mediaSyncLock, backupLock).filter { it.isLocked }.forEach { it.unlock() }
        }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        val tasks = ArrayDeque<Runnable>()

        override fun dispatch(
            context: CoroutineContext,
            block: Runnable,
        ) {
            tasks.addLast(block)
        }
    }
}
