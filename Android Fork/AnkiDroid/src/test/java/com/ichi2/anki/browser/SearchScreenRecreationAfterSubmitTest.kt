// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CardBrowser
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.search.StandardSearchFragment
import com.ichi2.anki.browser.search.requireChildFragment
import com.ichi2.anki.dialogs.DeckSelectionDialog
import com.ichi2.anki.libanki.DeckNameId
import com.ichi2.anki.settings.Prefs
import com.ichi2.testutils.withPref
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class SearchScreenRecreationAfterSubmitTest : RobolectricTest() {
    @Test
    fun `search deck picker survives background without an earlier search`() = probe(submitFirst = false, recreate = false)

    @Test
    fun `search deck picker survives recreation after an earlier search`() = probe(submitFirst = true, recreate = true)

    @Test
    fun `search deck picker survives background after an earlier search`() = probe(submitFirst = true, recreate = false)

    private fun probe(
        submitFirst: Boolean,
        recreate: Boolean,
    ) = withPref(Prefs::devUsingCardBrowserSearchView, true) {
        runTest {
            addBasicNote("Outside")
            val chosen = addDeck("Chosen").withNote()
            ActivityScenario.launch(CardBrowser::class.java).use { scenario ->
                lateinit var browser: CardBrowser
                scenario.onActivity { browser = it }
                val fragment = browser.cardBrowserFragment
                val search = assertNotNull(fragment.searchView)
                if (submitFirst) {
                    search.show()
                    advanceRobolectricLooper()
                    fragment.searchViewModel.setDecksFilter(listOf(DeckNameId("Chosen", chosen)))
                    search.editText.onEditorAction(IME_ACTION_SEARCH)
                    advanceRobolectricLooper()
                    fragment.activityViewModel.searchJob?.join()
                    advanceRobolectricLooper()
                    assertEquals(chosen, fragment.activityViewModel.deckId, "precondition: search submitted")
                    assertNull(fragment.childFragmentManager.findFragmentByTag(StandardSearchFragment.TAG), "precondition: closed")
                }
                search.show()
                advanceRobolectricLooper()
                val standard = fragment.requireChildFragment<StandardSearchFragment>(StandardSearchFragment.TAG)
                assertTrue(standard.binding.decksChip.performClick())
                advanceRobolectricLooper()
                standard.requireChildFragment<DeckSelectionDialog>(DeckSelectionDialog.TAG)
                if (recreate) {
                    scenario.recreate()
                } else {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    advanceRobolectricLooper()
                    scenario.moveToState(Lifecycle.State.RESUMED)
                }
                advanceRobolectricLooper()
                scenario.onActivity { browser = it }
                val restoredStandard =
                    assertNotNull(
                        browser.cardBrowserFragment.childFragmentManager.findFragmentByTag(StandardSearchFragment.TAG),
                        "search screen closed (recreate=$recreate)",
                    )
                assertNotNull(restoredStandard.childFragmentManager.findFragmentByTag(DeckSelectionDialog.TAG), "deck picker lost")
            }
        }
    }
}
