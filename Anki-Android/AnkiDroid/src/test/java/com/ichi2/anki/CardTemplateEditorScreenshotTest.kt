// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.test.core.app.ActivityScenario
import org.junit.Test

class CardTemplateEditorScreenshotTest : ScreenshotTest() {
    @Test
    fun basic() {
        val collectionBasicNoteTypeOriginal = getCurrentDatabaseNoteTypeCopy("Basic")
        val intent = CardTemplateEditor.getIntent(targetContext, collectionBasicNoteTypeOriginal.id)

        ActivityScenario.launch<CardTemplateEditor>(intent).use { scenario ->
            scenario.onActivity {
                captureScreen("basic")
            }
        }
    }
}
