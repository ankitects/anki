// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki.previewer

import androidx.test.core.app.ActivityScenario
import com.ichi2.anki.NotetypeFile
import com.ichi2.anki.ScreenshotTest
import com.ichi2.testutils.createTransientDirectory
import org.junit.Test

class TemplatePreviewerScreenshotTest : ScreenshotTest() {
    @Test
    fun baseState() {
        val notetype = col.notetypes.basic
        val notetypeFile = NotetypeFile(createTransientDirectory(), notetype)
        val arguments =
            TemplatePreviewerArguments(
                notetypeFile = notetypeFile,
                fields = listOf("Front", "Back"),
                tags = emptyList(),
            )

        val intent = TemplatePreviewerPage.getIntent(targetContext, arguments)

        ActivityScenario.launch<CardViewerActivity>(intent).use { scenario ->
            scenario.onActivity {
                captureScreen("base")
            }
        }
    }
}
