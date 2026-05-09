// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.previewer

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.browser.IdsFile
import com.ichi2.testutils.createTransientDirectory
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PreviewerScreenshotTest : ScreenshotTest() {
    @Test
    fun baseState() {
        val note = addBasicAndReversedNote()
        val intent =
            PreviewerFragment.getIntent(
                targetContext,
                idsFile = IdsFile(createTransientDirectory(), note.cardIds(col)),
                currentIndex = 0,
            )

        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.onActivity {
                captureScreen("base")
            }
        }
    }
}
