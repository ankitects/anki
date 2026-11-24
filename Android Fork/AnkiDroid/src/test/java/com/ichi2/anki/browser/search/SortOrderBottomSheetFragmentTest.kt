// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser.search

import android.os.Bundle
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.browser.BrowserColumnKey
import com.ichi2.anki.browser.createCardBrowserViewModel
import com.ichi2.anki.browser.search.SortOrderBottomSheetFragment.Companion.ARG_CURRENT_SORT_TYPE
import com.ichi2.anki.model.SortType
import com.ichi2.anki.testutils.SingleViewModelFactory
import com.ichi2.testutils.launchFragment
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests [SortOrderBottomSheetFragment] */
@RunWith(AndroidJUnit4::class)
class SortOrderBottomSheetFragmentTest : RobolectricTest() {
    private val defaultBrowserColumnKey = BrowserColumnKey("noteFld")

    fun sampleSortType() =
        SortType.CollectionOrdering(
            key = defaultBrowserColumnKey,
            reverse = true,
        )

    @Test
    fun `sort order argument is used`() {
        withFragment(currentSortType = sampleSortType()) {
            assertEquals(
                SortType.CollectionOrdering(
                    key = defaultBrowserColumnKey,
                    reverse = true,
                ),
                this.currentSortType,
            )
        }
    }

    fun withFragment(
        currentSortType: SortType = SortType.NoOrdering,
        block: suspend SortOrderBottomSheetFragment.() -> Unit,
    ) {
        val viewModelFactory = SingleViewModelFactory.create(createCardBrowserViewModel())

        val fragmentArgs =
            Bundle().apply {
                putParcelable(ARG_CURRENT_SORT_TYPE, currentSortType)
            }
        launchFragment(fragmentArgs) {
            SortOrderBottomSheetFragment(viewModelFactory)
        }.use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)

            scenario.onFragment { fragment ->
                runBlocking {
                    block(fragment)
                }
            }
        }
    }
}
