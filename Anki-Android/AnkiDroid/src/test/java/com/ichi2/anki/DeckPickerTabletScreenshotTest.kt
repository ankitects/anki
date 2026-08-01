// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.testutils.BackupManagerTestUtilities
import org.junit.After
import org.junit.Before
import org.junit.Test

/**
 * Screenshot tests for [DeckPicker] in fragmented mode.
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.DeckPickerTabletScreenshotTest"`
 */
class DeckPickerTabletScreenshotTest : ScreenshotTest() {
    @Before
    override fun setUp() {
        super.setUp()
        setTabletQualifiers()
    }

    @After
    fun tearDownBackup() {
        BackupManagerTestUtilities.reset()
    }

    @Test
    fun deckPickerWith30Decks() =
        withDeckPicker(deckCount = 30, withCards = true) { deckPicker ->
            deckPicker.simulateEdgeToEdge()
            // Allow tryShowStudyOptionsPanel()'s async fragment commit to finalize
            // before capturing, otherwise the right pane is empty.
            advanceRobolectricLooper()
            advanceRobolectricLooper()
            captureScreen("30_decks_tablet")
        }
}
