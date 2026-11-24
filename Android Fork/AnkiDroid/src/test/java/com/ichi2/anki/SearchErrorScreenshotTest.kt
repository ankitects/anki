// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import org.junit.Test
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `./gradlew :AnkiDroid:verifyRoborazziPlayDebug -Pscreenshot --tests "com.ichi2.anki.SearchErrorScreenshotTest"`
 */
class SearchErrorScreenshotTest : ScreenshotTest() {
    @Test
    fun `browser search errors`() =
        runTest {
            throwOnShowError = false
            addBasicNote()
            val browser = startRegularActivity<CardBrowser>()
            browser.waitForSearchResults()
            val fragment = browser.cardBrowserFragment
            for ((query, name) in listOf("and" to "inline_code", "re:b[" to "regex_diagnostic")) {
                // Submit through the UI: a fast error can skip the transient Searching state
                // which normally expands the toolbar when searching via the ViewModel.
                requireNotNull(fragment.searchItem).expandActionView()
                requireNotNull(fragment.legacySearchView).setQuery(query, true)
                browser.viewModel.searchJob?.join()
                advanceRobolectricLooper()
                assertTrue(requireNotNull(fragment.searchItem).isActionViewExpanded)
                assertEquals(query, requireNotNull(fragment.legacySearchView).query.toString())
                val dialog = requireNotNull(ShadowDialog.getLatestDialog())
                assertTrue(dialog.isShowing)
                captureScreen(name)
                dialog.dismiss()
            }
        }
}
