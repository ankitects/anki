// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.dialogs

import com.google.testing.junit.testparameterinjector.TestParameter
import com.ichi2.anki.ScreenshotTest
import com.ichi2.anki.getBrowserWithNotes
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.utils.ext.defaultBrowserSearch
import org.junit.Test

class BrowserOptionsDialogScreenshotTest : ScreenshotTest() {
    @Test
    fun `default search text`(
        @TestParameter hasDefaultSearch: Boolean,
    ) = runTest {
        col.config.defaultBrowserSearch = if (hasDefaultSearch) "deck:current" else ""
        val previousSearchView = Prefs.devUsingCardBrowserSearchView
        try {
            Prefs.devUsingCardBrowserSearchView = true
            val browser = getBrowserWithNotes(noteCount = 1)
            browser.viewModel.searchJob?.join()
            advanceRobolectricLooper()

            browser.cardBrowserFragment.showOptionsDialog()
            advanceRobolectricLooper()

            val dialog =
                browser.supportFragmentManager.fragments
                    .filterIsInstance<BrowserOptionsDialog>()
                    .single()
                    .requireDialog()
            captureView(
                if (hasDefaultSearch) "default_search_populated" else "default_search_empty",
                dialog.window!!.decorView,
            )
        } finally {
            Prefs.devUsingCardBrowserSearchView = previousSearchView
        }
    }
}
