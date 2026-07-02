// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Brayan Oliveira <brayandso.dev@gmail.com>

package com.ichi2.anki

import androidx.core.content.edit
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Screenshot tests for [DeckPicker]
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.DeckPickerScreenshotTest"`
 */
class DeckPickerScreenshotTest : ScreenshotTest() {
    @Before
    override fun setUp() {
        super.setUp()
        setPhoneQualifiers()
        ensureCollectionLoadIsSynchronous()
        setIntroductionSlidesShown(true)
        BackupManagerTestUtilities.setupSpaceForBackup(targetContext)
        // suppress the periodic 'backup your collection' prompt so the screenshot is just the activity
        targetContext.sharedPrefs().edit { putBoolean("backupPromptDisabled", true) }
    }

    @After
    fun tearDownBackup() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun baseState_and_fabExpanded() =
        withDeckPicker(deckCount = 0) { deckPicker ->
            captureScreen("baseState")

            deckPicker.floatingActionMenu.showFloatingActionMenu()
            captureScreen("fabExpanded")
        }

    @Test
    fun edgeToEdge_30_decks() =
        withDeckPicker(deckCount = 30) { deckPicker ->
            deckPicker.simulateEdgeToEdge()
            captureScreen("edgeToEdge_30_decks")
        }
}
