// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.CollectionManager.TR
import com.ichi2.anki.dialogs.SyncErrorDialog
import org.junit.Test

/**
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.CollectionTooLargeScreenshotTest"`
 */
class CollectionTooLargeScreenshotTest : ScreenshotTest() {
    @Test
    fun `collection too large dialog`() =
        withDeckPicker(deckCount = 0) { deckPicker ->
            val message = TR.syncUploadTooLarge("150 MB")

            deckPicker.showSyncErrorDialog(
                SyncErrorDialog.Type.DIALOG_COLLECTION_TOO_LARGE,
                message,
            )
            advanceRobolectricLooper()

            captureScreen("collection_too_large_dialog")
        }
}
