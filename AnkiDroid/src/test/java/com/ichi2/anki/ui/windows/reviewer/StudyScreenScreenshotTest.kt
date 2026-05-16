/*
 *  Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer

import androidx.test.core.app.ActivityScenario
import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.previewer.CardViewerActivity
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.settings.enums.FrameStyle
import com.ichi2.anki.settings.enums.ToolbarPosition
import org.junit.Test

class StudyScreenScreenshotTest : ScreenshotTest() {
    @Test
    fun captureScreenshot(
        @TestParameter toolbarPosition: ToolbarPosition,
        @TestParameter showAnswerButtons: Boolean,
        @TestParameter frameStyle: FrameStyle,
    ) {
        Prefs.isNewStudyScreenEnabled = true
        Prefs.toolbarPosition = toolbarPosition
        Prefs.showAnswerButtons = showAnswerButtons
        Prefs.frameStyle = frameStyle

        val configName =
            "bar=${toolbarPosition.name}_" +
                "frame=${frameStyle.name}bttns=$showAnswerButtons"

        ActivityScenario
            .launch<CardViewerActivity>(
                ReviewerFragment.getIntent(targetContext),
            ).use { scenario ->
                scenario.onActivity {
                    captureScreen(configName)
                }
            }
    }
}
