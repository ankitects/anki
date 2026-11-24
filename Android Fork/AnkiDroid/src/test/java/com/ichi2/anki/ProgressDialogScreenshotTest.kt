// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import android.content.DialogInterface
import org.junit.Test
import com.ichi2.anki.common.android.R as CommonR

/**
 * The progress dialog shown by `withProgressDialog`, with a manual cancel button.
 *
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot -Ptheme=all --tests "com.ichi2.anki.ProgressDialogScreenshotTest"`
 */
class ProgressDialogScreenshotTest : ScreenshotTest() {
    @Suppress("Deprecation") // ProgressDialog, as used by withProgressDialog
    @Test
    fun `progress dialog with a cancel button`() =
        withDeckPicker(deckCount = 0) { deckPicker ->
            android.app.ProgressDialog(deckPicker, CommonR.style.AppCompatProgressDialogStyle).apply {
                setMessage(deckPicker.getString(CommonR.string.dialog_processing))
                setButton(DialogInterface.BUTTON_NEGATIVE, deckPicker.getString(android.R.string.cancel)) { _, _ -> }
                show()
            }
            advanceRobolectricLooper()

            captureScreen("progress_dialog")
        }
}
