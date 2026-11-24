// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.common.time.MockTime
import com.ichi2.anki.common.time.TimeManager
import com.ichi2.anki.dialogs.SyncErrorDialog
import org.junit.Test

/**
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.SyncClockErrorScreenshotTest"`
 */
class SyncClockErrorScreenshotTest : ScreenshotTest() {
    @Test
    fun `clock error dialog`() =
        withDeckPicker(deckCount = 0) { deckPicker ->
            TimeManager.resetWith(MockTime(1_600_000_000_000))
            deckPicker.showSyncErrorDialog(
                SyncErrorDialog.Type.DIALOG_SYNC_CLOCK_OFF,
                CollectionManager.TR.syncClockOff(),
            )
            advanceRobolectricLooper()

            captureScreen("clock_error_dialog")
        }
}
