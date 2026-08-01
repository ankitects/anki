// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Brayan Oliveira <brayandso.dev@gmail.com>

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
