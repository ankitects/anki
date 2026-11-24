// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Intent
import android.view.Menu
import com.ichi2.anki.BottomNavController.NavigationItem
import com.ichi2.anki.DeckPicker
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.setIntroductionSlidesShown
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(ParameterizedRobolectricTestRunner::class)
class BrowserMultiSelectMenuTest : RobolectricTest() {
    @ParameterizedRobolectricTestRunner.Parameter
    @JvmField
    var embedded: Boolean = false

    @Test
    fun `deselecting a row updates the menu - Issue 23269`() {
        addBasicNote("one")
        addBasicNote("two")
        withBrowser {
            toggleRowAt(0)
            toggleRowAt(1)
            assertFalse(menu.findItem(R.id.action_edit_note).isVisible, "two rows selected")

            toggleRowAt(1)
            assertTrue(menu.findItem(R.id.action_edit_note).isVisible, "one row selected")
        }
    }

    private val CardBrowserFragment.menu: Menu
        get() = searchBar!!.menu

    private suspend fun CardBrowserFragment.toggleRowAt(position: Int) {
        activityViewModel.toggleRowSelection(activityViewModel.cards[position].toRowSelection()).join()
        advanceRobolectricLooper()
    }

    private fun withBrowser(block: suspend CardBrowserFragment.() -> Unit) =
        if (embedded) {
            withPref(Prefs::devBottomNavEnabled, true) {
                withPref(Prefs::devUsingCardBrowserSearchView, true) {
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
            }
        } else {
            withCardBrowserFragment(useSearchView = true) {
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
