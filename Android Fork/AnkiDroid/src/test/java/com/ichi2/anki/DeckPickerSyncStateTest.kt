// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DeckPickerSyncStateTest : RobolectricTest() {
    @Test
    fun `conflict metered approval survives activity recreation`() {
        setIntroductionSlidesShown(true)
        ActivityScenario.launch(DeckPicker::class.java).use { scenario ->
            advanceRobolectricLooper()
            scenario.onActivity { activity ->
                activity.mediaUsnOnConflict = 42
                activity.mediaNetworkTypeOnConflict = NetworkType.CONNECTED
            }

            scenario.recreate()
            advanceRobolectricLooper()

            scenario.onActivity { activity ->
                assertThat(activity.mediaUsnOnConflict, equalTo(42))
                assertThat(activity.mediaNetworkTypeOnConflict, equalTo(NetworkType.CONNECTED))
            }
        }
    }
}
