// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
package com.ichi2.anki

import androidx.core.content.edit
import com.ichi2.anki.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.Before
import org.junit.Test

class DeckPickerScreenshotTest : ScreenshotTest() {
    @Before
    override fun setUp() {
        super.setUp()
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        // suppress the periodic 'backup your collection' prompt so the screenshot is just the activity
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @Test
    fun baseState_and_fabExpanded() {
        val intent = DeckPicker.getIntent(targetContext)
        val activity = startActivityNormallyOpenCollectionWithIntent(DeckPicker::class.java, intent)
        captureScreen("baseState")

        activity.floatingActionMenu.showFloatingActionMenu()
        captureScreen("fabExpanded")
    }
}
