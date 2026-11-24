// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.settings.Prefs
import com.ichi2.anki.throwOnShowError
import com.ichi2.testutils.withPref
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.shadows.ShadowDialog
import kotlin.test.assertContains
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class CardBrowserSearchErrorTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var embedded: Boolean = false

    @Test
    fun `search errors are displayed in each browser host - Issue 23301`() =
        withBrowser {
            throwOnShowError = false
            repeat(2) {
                activityViewModel.setQuery("is:now").join()
                awaitSearch()

                assertSearchError("now").dismiss()
            }
        }

    private fun assertSearchError(expected: String): AlertDialog {
        val dialog = assertNotNull(ShadowDialog.getLatestDialog(), "search error is shown") as AlertDialog
        assertTrue(dialog.isShowing, "expected a new error dialog containing '$expected'")
        val message = assertNotNull(dialog.findViewById<TextView>(android.R.id.message)).text.toString()
        assertContains(message, expected)
        return dialog
    }

    private suspend fun CardBrowserFragment.awaitSearch() {
        activityViewModel.searchJob?.join()
        advanceRobolectricLooper()
    }

    private fun withBrowser(
        useSearchView: Boolean = false,
        block: suspend CardBrowserFragment.() -> Unit,
    ) = if (embedded) {
        withPref(Prefs::devBottomNavEnabled, true) {
            setIntroductionSlidesShown(true)
            runTest {
                val activity = startActivityControllerNormallyOpenCollectionWithIntent(DeckPicker::class.java, Intent()).get()
                activity.binding.bottomNavigation!!.selectedItemId = NavigationItem.BROWSER.id
                advanceRobolectricLooper()
                val browser = activity.supportFragmentManager.findFragmentByTag(NavigationItem.BROWSER.tag) as CardBrowserFragment
                browser.activityViewModel.searchJob?.join()
                advanceRobolectricLooper()
                block(browser)
            }
        }
    } else {
        withCardBrowserFragment(useSearchView = useSearchView) {
            activityViewModel.searchJob?.join()
            advanceRobolectricLooper()
            block()
        }
    }

    companion object {
        @ParameterizedRobolectricTestRunner.Parameters(name = "embedded={0}")
        @JvmStatic
        fun parameters() = listOf(arrayOf(false), arrayOf(true))
    }
}
