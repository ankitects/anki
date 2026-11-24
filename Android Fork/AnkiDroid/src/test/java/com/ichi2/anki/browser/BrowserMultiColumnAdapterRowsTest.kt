// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.view.ContextThemeWrapper
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith
import com.ichi2.anki.common.android.R as CommonR

@RunWith(AndroidJUnit4::class)
class BrowserMultiColumnAdapterRowsTest : RobolectricTest() {
    private val browserContext by lazy { ContextThemeWrapper(targetContext, CommonR.style.Theme_AnkiDroid) }

    @Test
    fun `scroll during search refresh does not crash - Issue 17759`() =
        runCardBrowserViewModelTest(notes = 20) {
            val adapter = createAdapterWithSearchResults()
            val recyclerView = adapter.createLaidOutRecyclerView()

            cards.reset()
            recyclerView.scrollBy(0, 200)
        }

    @Test
    fun `clearing search results does not change adapter rows before notification - Issue 17759`() =
        runCardBrowserViewModelTest(notes = 2) {
            val adapter = createAdapterWithSearchResults()
            val originalRows = cards.toList()
            assertThat(originalRows.size, equalTo(2))

            // A search clears the ViewModel before the fragment collects its update flow.
            // RecyclerView can lay out the previous results during that interval.
            cards.reset()

            assertThat(adapter.itemCount, equalTo(originalRows.size))
            val holder = adapter.onCreateViewHolder(FrameLayout(browserContext), 0)
            adapter.onBindViewHolder(holder, 1)
            assertThat(holder.id, equalTo(originalRows[1]))

            val notifiedCounts = adapter.recordNotifiedItemCounts()
            adapter.refreshRows()
            assertThat(adapter.itemCount, equalTo(0))
            assertThat(notifiedCounts, equalTo(listOf(0)))

            cards.replaceWith(cardsOrNotes, originalRows.reversed())
            assertThat(adapter.itemCount, equalTo(0))
            adapter.refreshRows()
            assertThat(notifiedCounts, equalTo(listOf(0, 2)))
            adapter.onBindViewHolder(holder, 0)
            assertThat(holder.id, equalTo(originalRows[1]))
        }

    private suspend fun CardBrowserViewModel.createAdapterWithSearchResults(): BrowserMultiColumnAdapter {
        launchSearchForCards()
        searchJob?.join()
        return BrowserMultiColumnAdapter(browserContext, this, onLongPress = {}, onTap = {})
    }

    private fun BrowserMultiColumnAdapter.createLaidOutRecyclerView(): RecyclerView =
        RecyclerView(browserContext).apply {
            layoutManager = LinearLayoutManager(browserContext)
            adapter = this@createLaidOutRecyclerView
            measure(
                MeasureSpec.makeMeasureSpec(1080, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(200, MeasureSpec.EXACTLY),
            )
            layout(0, 0, 1080, 200)
        }

    private fun BrowserMultiColumnAdapter.recordNotifiedItemCounts(): List<Int> {
        val counts = mutableListOf<Int>()
        registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    counts.add(itemCount)
                }
            },
        )
        return counts
    }
}
