// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.ioDispatcher
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.setMain
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.coroutines.CoroutineContext
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
class SwitchProfilesReentryRegressionTest : RobolectricTest() {
    @Test
    fun `reopening the screen cannot let an older switch replace a later confirmation`() {
        val mainScheduler = TestCoroutineScheduler()
        Dispatchers.setMain(StandardTestDispatcher(mainScheduler))
        val queuedIo = QueuedDispatcher()
        ioDispatcher = queuedIo
        val app = AnkiDroidApp.instance
        val manager = requireNotNull(app.profileManager)
        val work = manager.createNewProfile(name("Work"))
        val personal = manager.createNewProfile(name("Personal"))
        val registry = app.baseContext.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
        val profilesAtRestart = mutableListOf<String?>()

        fun model() =
            SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) {
                profilesAtRestart += registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null)
            }
        val firstStore = ViewModelStore()
        val secondStore = ViewModelStore()
        try {
            val first = model().also { firstStore.put("profiles", it) }
            first.requestSwitch(first.profiles.value.single { it.id == work })
            first.switchProfile()
            firstStore.clear() // Back navigation clears the first Fragment's ViewModelStore.

            val second = model().also { secondStore.put("profiles", it) }
            second.requestSwitch(second.profiles.value.single { it.id == personal })
            // Refusing another switch while one is pending is also a valid outcome.
            val lastConfirmed = second.profileToSwitchTo.value?.id ?: work
            second.switchProfile()

            // IO is a pool: the newer save may finish before the older one.
            while (queuedIo.tasks.isNotEmpty()) queuedIo.tasks.removeLast().run()
            mainScheduler.runCurrent()

            assertEquals(
                lastConfirmed.value,
                registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null),
                "The original screen overwrote the last confirmation after it was destroyed",
            )
            assertEquals<List<String?>>(listOf(lastConfirmed.value), profilesAtRestart, "Only one process handover should be requested")
        } finally {
            firstStore.clear()
            secondStore.clear()
            while (queuedIo.tasks.isNotEmpty()) queuedIo.tasks.removeFirst().run()
            mainScheduler.runCurrent()
        }
    }

    private fun name(value: String) = (ProfileName.validate(value) as ProfileName.ValidationResult.Valid).name

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
