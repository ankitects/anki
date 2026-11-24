// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.browser

import android.content.Context
import android.view.MotionEvent
import android.view.View
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.R
import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem.ColumnItem
import com.ichi2.anki.browser.BrowserColumnSelectionRecyclerItem.UsageItem
import com.ichi2.anki.browser.ColumnUsage.ACTIVE
import com.ichi2.anki.browser.ColumnUsage.AVAILABLE
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.changedPositions
import com.ichi2.testutils.completeDroppedItem
import com.ichi2.testutils.dispatchTouch
import com.ichi2.testutils.dropDraggedItem
import com.ichi2.testutils.scrollToEnd
import com.ichi2.testutils.withViewOnScreen
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for [BrowserColumnSelectionTouchHelperCallback] */
@RunWith(AndroidJUnit4::class)
class BrowserColumnSelectionTouchHelperCallbackTest : AndroidTest {
    @Test
    fun `all rows are refreshed once a dropped row completes`() =
        withColumnSelectionList { list ->
            val changedRows = list.adapter!!.changedPositions()
            list.startDrag(row = 1)
            list.dropDraggedItem()
            assertThat("not refreshed before drop completes", changedRows, empty())

            list.completeDroppedItem()

            assertThat("refreshed after drop completes", changedRows, equalTo(list.allRows))
        }

    @Test
    fun `scrolling a dropped row off-screen before it completes does not crash`() =
        withColumnSelectionList { list ->
            val changedRows = list.adapter!!.changedPositions()
            list.startDrag(row = 1)
            list.dropDraggedItem()

            // recycling the row cancels its animation, which calls clearView mid-scroll
            list.scrollToEnd()

            assertThat("refreshed after scrolling", changedRows, equalTo(list.allRows))
        }

    /** Displays the 'manage columns' list in a window a few rows tall, so a scroll recycles rows */
    private fun withColumnSelectionList(block: (RecyclerView) -> Unit) =
        withViewOnScreen(height = LIST_HEIGHT_PX, createView = ::columnSelectionList) { list ->
            check(list.canScrollVertically(1)) { "the list should not fit on screen" }
            block(list)
        }

    /** Two active columns and the rest available, draggable via [BrowserColumnSelectionTouchHelperCallback] */
    private fun columnSelectionList(context: Context): RecyclerView {
        val columns = CardBrowserColumn.entries.map { ColumnWithSample(label = it.name, columnType = it, sampleValue = null) }
        val items =
            buildList {
                add(UsageItem(ACTIVE))
                addAll(columns.take(2).map(::ColumnItem))
                add(UsageItem(AVAILABLE))
                addAll(columns.drop(2).map(::ColumnItem))
            }.toMutableList()
        val itemTouchHelper = ItemTouchHelper(BrowserColumnSelectionTouchHelperCallback(items))
        return RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter =
                BrowserColumnSelectionAdapter(items).apply {
                    setOnDragHandleTouchedListener { itemTouchHelper.startDrag(it) }
                }
            itemTouchHelper.attachToRecyclerView(this)
        }
    }

    /** Touches the drag handle of [row], which starts a drag */
    private fun RecyclerView.startDrag(row: Int) {
        val dragHandle = findViewHolderForAdapterPosition(row)!!.itemView.findViewById<View>(R.id.drag_handle)
        dragHandle.dispatchTouch(MotionEvent.ACTION_DOWN)
    }

    private val RecyclerView.allRows: Set<Int>
        get() = (0 until adapter!!.itemCount).toSet()

    companion object {
        /** Fits a few rows, leaving the rest off-screen */
        private const val LIST_HEIGHT_PX = 200
    }
}
