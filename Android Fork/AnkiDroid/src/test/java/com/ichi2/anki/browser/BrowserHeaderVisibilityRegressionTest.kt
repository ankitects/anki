// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.view.KeyEvent.KEYCODE_S
import android.view.View
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.search.SavedSearch
import com.ichi2.anki.dialogs.SavedBrowserSearchesDialogFragment
import com.ichi2.anki.model.SelectableDeck
import com.ichi2.testutils.ext.snackbarAction
import com.ichi2.testutils.ext.snackbarText
import com.ichi2.testutils.pressKey
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class BrowserHeaderVisibilityRegressionTest : RobolectricTest() {
    @Test
    fun `saved search opened by shortcut reports its count during multi-select`() {
        addBasicNote("cat", "meows")
        addBasicNote("dog", "barks")
        withCardBrowserFragment {
            activityViewModel.setSelectedDeck(SelectableDeck.AllDecks)
            awaitSearch()
            assertEquals(SaveSearchResult.SUCCESS, activityViewModel.saveSearch(SavedSearch("Cats", "cat")))
            activityViewModel.selectAll()?.join()
            advanceRobolectricLooper()
            assertTrue(activityViewModel.isInMultiSelectMode)
            assertFalse(requireActivity().findViewById<TextView>(R.id.subtitle).isShown)

            onView(isRoot()).pressKey(KEYCODE_S, ctrl = true, alt = true)
            advanceRobolectricLooper()
            val dialog = parentFragmentManager.fragments.filterIsInstance<SavedBrowserSearchesDialogFragment>().single()
            val list = assertNotNull(dialog.requireDialog().findViewById<RecyclerView>(CommonR.id.dialog_recycler_view))
            assertNotNull(list.findViewHolderForAdapterPosition(0)).itemView.performClick()
            awaitSearch()

            assertEquals("cat", activityViewModel.searchTerms)
            assertEquals(1, activityViewModel.rowCount)
            assertTrue(activityViewModel.isInMultiSelectMode)
            assertTrue(assertNotNull(legacySearchView).isIconified)
            assertFalse(requireActivity().findViewById<TextView>(R.id.subtitle).isShown)
            assertEquals("1 card shown", snackbarText)
            assertEquals(View.GONE, snackbarAction?.visibility)
        }
    }

    private suspend fun CardBrowserFragment.awaitSearch() {
        activityViewModel.searchJob?.join()
        advanceRobolectricLooper()
    }
}
