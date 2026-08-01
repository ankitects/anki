// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.notetype

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.ScreenshotTest
import org.junit.Test

class ManageNotetypesScreenshotTest : ScreenshotTest() {
    @Test
    fun base_and_selected() {
        val intent = ManageNoteTypesDestination().toIntent(targetContext)
        ActivityScenario.launch<ManageNotetypes>(intent).use { scenario ->
            scenario.onActivity { activity ->
                captureScreen("base")

                activity.binding.appBarLayout.isLifted = true
                captureScreen("appbar_lifted")
                activity.binding.appBarLayout.isLifted = false

                // enable multi select mode by selecting the first entry
                val currentState = activity.viewModel.state.value
                activity.viewModel.onItemLongClick(currentState.noteTypes[0])
                captureScreen("multi_select_mode")
            }
        }
    }
}
