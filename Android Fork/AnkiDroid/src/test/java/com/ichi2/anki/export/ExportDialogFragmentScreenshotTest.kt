// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.export

import android.os.Bundle
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.libanki.Consts.DEFAULT_DECK_ID
import com.ichi2.testutils.launchFragment
import org.junit.Test

class ExportDialogFragmentScreenshotTest : ScreenshotTest() {
    @Test
    fun collapsed() =
        withExportDialog {
            captureScreen("collapsed")
        }

    @Test
    fun expanded() =
        withExportDialog {
            binding.exportTypeSelector.performClick()
            // BUG(Roborazzi): a popup is positioned relative to the screen, rather than the parent
            captureScreen("expanded")
        }

    @Test
    fun deck() =
        withExportDialog(ExportDialogFragment.newInstance(DEFAULT_DECK_ID).arguments) {
            captureScreen("deck")
        }

    private fun withExportDialog(
        fragmentArgs: Bundle? = null,
        action: ExportDialogFragment.() -> Unit,
    ) {
        ensureCollectionLoadIsSynchronous()
        launchFragment<ExportDialogFragment>(fragmentArgs).use { scenario ->
            scenario.onFragment { fragment ->
                advanceRobolectricLooper()
                action(fragment)
            }
        }
    }
}
