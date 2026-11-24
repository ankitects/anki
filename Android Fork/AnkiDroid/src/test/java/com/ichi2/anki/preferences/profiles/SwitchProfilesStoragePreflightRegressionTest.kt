// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.preferences.profiles

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.AnkiDroidApp
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.anki.exception.StorageAccessException
import com.ichi2.anki.multiprofile.ProfileId
import com.ichi2.anki.multiprofile.ProfileManager
import com.ichi2.anki.multiprofile.ProfileName
import com.ichi2.anki.multiprofile.ProfileSwitchGuard
import kotlinx.coroutines.test.advanceUntilIdle
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

@RunWith(AndroidJUnit4::class)
class SwitchProfilesStoragePreflightRegressionTest : RobolectricTest() {
    @Test
    fun `a target with an unusable external collection directory is refused before restart`() =
        runTest {
            val app = AnkiDroidApp.instance
            val manager = requireNotNull(app.profileManager)
            val target = manager.createNewProfile((ProfileName.validate("Work") as ProfileName.ValidationResult.Valid).name)
            val collectionDirectory = File(requireNotNull(app.getExternalFilesDir(null)), target.value)
            assertFalse(collectionDirectory.exists())
            collectionDirectory.writeText("a file where the collection directory belongs")
            var restarts = 0
            val model = SwitchProfilesViewModel(manager, ProfileSwitchGuard(manager, emptyList())) { restarts++ }
            try {
                assertFailsWith<StorageAccessException> { CollectionHelper.initializeAnkiDroidDirectory(collectionDirectory) }

                model.requestSwitch(model.profiles.value.single { it.id == target })
                model.switchProfile()
                advanceUntilIdle()

                val registry = app.baseContext.getSharedPreferences(ProfileManager.PROFILE_REGISTRY_FILENAME, Context.MODE_PRIVATE)
                assertEquals(0, restarts, "The target's collection cannot be opened, so the running profile must remain usable")
                assertEquals(ProfileId.DEFAULT.value, registry.getString(ProfileManager.PREF_LAST_ACTIVE_PROFILE_ID, null))
                assertEquals(target, model.profileSwitchFailed.value?.id)
            } finally {
                collectionDirectory.delete()
            }
        }
}
