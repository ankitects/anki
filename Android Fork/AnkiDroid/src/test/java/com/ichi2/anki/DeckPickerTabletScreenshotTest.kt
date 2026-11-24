// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.testutils.BackupManagerTestUtilities
import com.ichi2.testutils.simulateKeyboard
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment

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
    fun deckOverviewKeyboardOpen() =
        withDeckPicker(deckCount = 1, withCards = true) { deckPicker ->
            deckPicker.simulateKeyboard()
            captureScreen("keyboard_open")
        }

    @Test
    fun deckPickerWith30Decks() =
        withDeckPicker(deckCount = 30, withCards = true) { deckPicker ->
            deckPicker.simulateEdgeToEdge()
            captureScreen("30_decks_tablet")
        }

    @Test
    fun deckPickerWith30DecksRtl() =
        runTest {
            RuntimeEnvironment.setQualifiers("+ar")
            withDeckPicker(deckCount = 30, withCards = true) { deckPicker ->
                deckPicker.simulateEdgeToEdge()
                captureScreen("30_decks_tablet_rtl")
            }
        }
}
