// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import android.view.Gravity
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.closeTo
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class StableScrollbarLayoutManagerTest {
    @Test
    fun `refresh resizes thumb when scroller attaches before adapter`() {
        val list = ScrollbarTestList(List(90) { 100 }, attachBeforeAdapter = true)
        val expected = ScrollbarTestList(List(90) { 105 })

        list.refreshRows(List(90) { 105 })

        assertThat("thumb retained the old range", list.thumb.height, equalTo(expected.thumb.height))
    }

    @Test
    fun `scrolling tolerates a keyboard inset taller than the viewport`() {
        val list = ScrollbarTestList(List(90) { TALL_ROW })
        list.scrollBy(150)
        list.applyBottomPadding(2100)

        list.scrollBy(1)
    }

    @Test
    fun `changing only viewport height preserves the calibrated content range`() {
        val list = ScrollbarTestList(shortThenTallRows)
        list.scrollToEndCollecting { list.scrollRange }
        list.scrollToStartCollecting { list.scrollRange }
        val beforeRange = list.scrollRange

        list.resizeViewportHeight(1800)

        assertThat("viewport resizing must not discard content calibration", list.scrollRange, equalTo(beforeRange))
    }

    @Test
    fun `returning one pixel to the end preserves the thumb`() {
        val list = ScrollbarTestList(listOf(30, 1600, 100, 400, 30))
        list.scrollToEndCollecting { list.thumb.height }
        val beforeAnchor = list.visibleAnchor
        val beforeRange = list.scrollRange
        val beforeHeight = list.thumb.height

        list.scrollBy(-1)
        list.scrollBy(1)

        assertThat("content must return to the same endpoint", list.visibleAnchor, equalTo(beforeAnchor))
        assertThat("same endpoint must retain its range", list.scrollRange, equalTo(beforeRange))
        assertThat("same endpoint must retain its thumb size", list.thumb.height, equalTo(beforeHeight))
    }

    @Test
    fun `fine scrolling calibrates the same range as a single scroll`() {
        val rows = List(30) { TALL_ROW } + List(60) { SHORT_ROW }
        val fine = ScrollbarTestList(rows)
        val coarse = ScrollbarTestList(rows)

        repeat(rows.sum()) { fine.scrollBy(1) }
        coarse.scrollBy(rows.sum())

        assertThat("scroll step size must not change calibration", fine.scrollRange, equalTo(coarse.scrollRange))
    }

    @Test
    fun `reversing one pixel at the end preserves thumb size`() {
        val list = ScrollbarTestList(listOf(30, 1600, 100, 400, 30))
        list.scrollToEndCollecting { list.thumb.height }
        val heightAtEnd = list.thumb.height

        list.scrollBy(-1)

        assertThat(list.thumb.height, equalTo(heightAtEnd))
    }

    @Test
    fun `closing the keyboard preserves thumb size while scrolling`() {
        val rows = List(10) { 80 } + List(10) { 800 }
        val list = ScrollbarTestList(List(20) { SHORT_ROW })
        list.applyBottomPadding(1200)
        list.refreshRows(rows)
        list.applyBottomPadding(0)
        val initialSize = list.thumb.height

        val sizes =
            List(20) {
                list.scrollBy(50)
                list.thumb.height
            }

        assertThat(sizes, everyItem(equalTo(initialSize)))
    }

    @Test
    fun `unchanged refresh preserves calibrated range and thumb size`() {
        val list = ScrollbarTestList(shortThenTallRows)
        list.scrollToEndCollecting { list.scrollRange }
        list.scrollToStartCollecting { list.scrollRange }
        assertThat(list.scrollRange, equalTo(shortThenTallRows.sum()))
        val beforeRange = list.scrollRange
        val beforeHeight = list.thumb.height
        val beforeAnchor = list.visibleAnchor

        list.refreshRows()

        assertThat("refresh moved the content", list.visibleAnchor, equalTo(beforeAnchor))
        assertThat("unchanged refresh discarded the calibrated range", list.scrollRange, equalTo(beforeRange))
        assertThat("unchanged refresh resized the thumb", list.thumb.height, equalTo(beforeHeight))
    }

    @Test
    fun `jumping away and back restores thumb position`() {
        val list = ScrollbarTestList(listOf(200, 400, 1600, 100))
        list.scrollBy(200)
        val beforeTop = list.thumb.top
        val beforeRange = list.scrollRange
        val beforeAnchor = list.visibleAnchor

        list.jumpToLastRow()
        list.jumpToRow(beforeAnchor.first, beforeAnchor.second)

        assertThat("jump did not restore the content", list.visibleAnchor, equalTo(beforeAnchor))
        assertThat("jump changed the range", list.scrollRange, equalTo(beforeRange))
        assertThat("same content must restore the thumb", list.thumb.top.toDouble(), closeTo(beforeTop.toDouble(), 2.0))
    }

    @Test
    fun `unchanged refresh preserves thumb position`() {
        val list = ScrollbarTestList(listOf(200, 400, 1600, 100))
        list.scrollBy(200)
        val beforeTop = list.thumb.top
        val beforeRange = list.scrollRange
        val beforeAnchor = list.visibleAnchor

        list.refreshRows()

        assertThat("refresh moved the content", list.visibleAnchor, equalTo(beforeAnchor))
        assertThat("refresh changed the range", list.scrollRange, equalTo(beforeRange))
        assertThat("stationary content must keep the thumb in place", list.thumb.top.toDouble(), closeTo(beforeTop.toDouble(), 2.0))
    }

    @Test
    fun `thumb keeps moving before the last row enters view`() {
        val list = ScrollbarTestList(listOf(200, 400, 1600, 100))
        list.scrollBy(150)
        val beforeTop = list.thumb.top
        val beforeAnchor = list.visibleAnchor

        // The last row remains below the 2000-pixel viewport throughout this scroll.
        list.scrollBy(49)

        val expectedAnchor = beforeAnchor.first to beforeAnchor.second - 49
        assertThat("content must scroll the full requested distance", list.visibleAnchor, equalTo(expectedAnchor))
        assertThat("thumb stalled while content scrolled", list.thumb.top, greaterThan(beforeTop))
    }

    @Test
    fun `thumb never moves backwards when a short final row enters view`() {
        val list = ScrollbarTestList(listOf(200, 400, 1600, 100))
        val tops = list.scrollToEndCollecting { list.thumb.top }
        val backwards = tops.zipWithNext().firstOrNull { (before, after) -> after < before }
        assertThat("thumb tops: $tops; backwards: $backwards", backwards, nullValue())
    }

    @Test
    fun `inset changes cannot leave the range permanently miscalibrated`() {
        val rows = List(90) { SHORT_ROW }
        val list = ScrollbarTestList(rows)
        list.applyBottomPadding(300)
        list.scrollBy(7200)
        list.applyBottomPadding(0)
        list.scrollToStartCollecting { list.scrollRange }
        list.scrollToEndCollecting { list.scrollRange }
        assertThat("range after another full pass", list.scrollRange, equalTo(rows.sum()))
    }

    @Test
    fun `refresh and reanchor cannot resurrect an invalidated offset`() {
        val rows = List(90) { SHORT_ROW }
        val list = ScrollbarTestList(rows)
        list.scrollBy(4000)
        val changed = rows.toMutableList().apply { this[0] = 10 * SHORT_ROW }

        list.refreshAndReanchor(changed)
        list.scrollToEndCollecting { list.scrollRange }
        // An incorrectly calibrated range must not survive subsequent full passes.
        list.scrollToStartCollecting { list.scrollRange }
        list.scrollToEndCollecting { list.scrollRange }

        assertThat(list.scrollRange, equalTo(changed.sum()))
    }

    @Test
    fun `single row update and reanchor cannot resurrect an invalidated offset`() {
        val rows = List(90) { SHORT_ROW }
        val list = ScrollbarTestList(rows)
        list.scrollBy(4000)
        val changed = rows.toMutableList().apply { this[0] = 2 * SHORT_ROW }

        list.refreshAndReanchor(changed, changedPosition = 0)
        list.scrollToEndCollecting { list.scrollRange }
        list.scrollToStartCollecting { list.scrollRange }
        list.scrollToEndCollecting { list.scrollRange }

        assertThat(list.scrollRange, equalTo(changed.sum()))
    }

    @Test
    fun `updating an unchanged visible row preserves the calibrated thumb`() {
        val list = ScrollbarTestList(shortThenTallRows)
        list.scrollToEndCollecting { list.scrollRange }
        list.scrollToStartCollecting { list.scrollRange }
        list.scrollBy(1000)
        assertThat(list.scrollRange, equalTo(shortThenTallRows.sum()))
        val before = list.thumb.height

        list.refreshFirstVisibleRow()

        assertThat("range after row update: ${list.scrollRange}", list.thumb.height, equalTo(before))
        assertThat(list.scrollRange, equalTo(shortThenTallRows.sum()))
    }

    @Test
    fun `thumb never moves backwards on a short mixed height list`() {
        val list = ScrollbarTestList(List(4) { 800 } + List(4) { 200 })

        val tops = list.scrollToEndCollecting { list.thumb.top }
        val backwardsStep = tops.zipWithNext().firstOrNull { (before, after) -> after < before }

        assertThat("thumb moved backwards: $backwardsStep", backwardsStep, nullValue())
    }

    @Test
    fun `tall initial rows calibrate to actual content height`() {
        val rows = List(30) { TALL_ROW } + List(60) { SHORT_ROW }
        val list = ScrollbarTestList(rows)
        list.scrollToEndCollecting { list.thumb.height }
        assertThat(list.scrollRange, equalTo(rows.sum()))
    }

    @Test
    fun `calibration does not depend on scroll step size`() {
        val rows = List(30) { TALL_ROW } + List(60) { SHORT_ROW }
        val incremental = ScrollbarTestList(rows)
        val singleStep = ScrollbarTestList(rows)
        incremental.scrollToEndCollecting { incremental.thumb.height }
        singleStep.scrollBy(rows.sum())
        assertThat(incremental.scrollRange, equalTo(singleStep.scrollRange))
    }

    @Test
    fun `calibrated range is invalidated when an individual offscreen row changes height`() {
        val rows = List(90) { SHORT_ROW }
        val list = ScrollbarTestList(rows)
        list.scrollToEndCollecting { list.thumb.height }
        assertThat(list.scrollRange, equalTo(rows.sum()))
        list.scrollToStartCollecting { list.thumb.height }
        val changedRows = rows.toMutableList().apply { this[lastIndex] = TALL_ROW }
        list.updateRowHeights(changedRows, changedRows.lastIndex)
        list.scrollToEndCollecting { list.thumb.height }
        // Visiting every row again must allow the changed height to be measured.
        list.scrollToStartCollecting { list.thumb.height }
        list.scrollToEndCollecting { list.thumb.height }
        assertThat(list.scrollRange, equalTo(changedRows.sum()))
    }

    @Test
    fun `thumb never moves backwards when the initial rows are taller`() {
        val list = ScrollbarTestList(List(30) { TALL_ROW } + List(60) { SHORT_ROW })

        val tops = list.scrollToEndCollecting { list.thumb.top }
        val backwardsStep = tops.zipWithNext().firstOrNull { (before, after) -> after < before }

        assertThat("thumb moved backwards: $backwardsStep", backwardsStep, nullValue())
    }

    @Test
    fun `calibration accounts for height changes above the viewport`() {
        val rows = List(90) { SHORT_ROW }
        val list = ScrollbarTestList(rows)
        list.scrollBy(4000)
        val changedRows = rows.toMutableList().apply { this[0] = 10 * SHORT_ROW }

        // A full refresh can change rows above the viewport without resizing any attached row.
        list.refreshRows(changedRows)
        list.scrollToEndCollecting { list.thumb.height }
        list.scrollToStartCollecting { list.thumb.height }
        list.scrollToEndCollecting { list.thumb.height }

        assertThat(list.scrollRange, equalTo(changedRows.sum()))
    }

    @Test
    fun `calibrated range is invalidated when offscreen rows change height`() {
        val list = ScrollbarTestList(List(90) { SHORT_ROW })
        list.scrollToEndCollecting { list.thumb.height }
        list.scrollToStartCollecting { list.thumb.height }
        val changedRows = List(60) { SHORT_ROW } + List(30) { TALL_ROW }

        // Toggling browser truncation can resize only the long rows below the viewport.
        list.refreshRows(changedRows)
        list.scrollToEndCollecting { list.thumb.height }

        assertThat(list.scrollRange, equalTo(changedRows.sum()))
    }

    @Test
    fun `returning to same content restores thumb position`() {
        val list = ScrollbarTestList(shortThenTallRows)
        repeat(20) { list.scrollBy(50) }
        val before = list.thumb.top
        repeat(260) { list.scrollBy(50) }
        repeat(260) { list.scrollBy(-50) }
        assertThat(list.thumb.top.toDouble(), closeTo(before.toDouble(), 2.0))
    }

    @Test
    fun `thumb calibrated after visiting every row`() {
        val list = ScrollbarTestList(shortThenTallRows)
        list.scrollToEndCollecting { list.thumb.height }
        val expected = list.trackHeight.toDouble() * 2000 / shortThenTallRows.sum()
        assertThat(list.thumb.height.toDouble(), closeTo(expected, 2.0))
    }

    @Test
    fun `thumb is calibrated on the next pass after the rows refresh on the way`() {
        val list = ScrollbarTestList(shortThenTallRows)
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        list.refreshRows()
        list.scrollToEndCollecting { list.thumb.height }
        list.scrollToStartCollecting { list.thumb.height }
        list.scrollToEndCollecting { list.thumb.height }

        val expected = list.trackHeight.toDouble() * 2000 / shortThenTallRows.sum()
        assertThat(list.thumb.height.toDouble(), closeTo(expected, 2.0))
    }

    @Test
    fun `jumping to the last row keeps the estimated size`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        list.jumpToLastRow()

        assertThat(list.thumb.height, equalTo(initialSize))
    }

    @Test
    fun `reanchoring same row does not move thumb`() {
        val list = ScrollbarTestList(shortThenTallRows)
        repeat(100) { list.scrollBy(50) }
        val before = list.thumb.top
        list.reanchor()
        assertThat(list.thumb.top.toDouble(), closeTo(before.toDouble(), 2.0))
    }

    @Test
    fun `thumb keeps its size while rows of different heights scroll past`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        val sizesBeforeTheEnd = list.scrollToEndCollecting { list.thumb.height }.dropLast(1)

        assertThat(sizesBeforeTheEnd, everyItem(equalTo(initialSize)))
    }

    @Test
    fun `thumb reaches the end of the track at the end of the list`() {
        val list = ScrollbarTestList(shortThenTallRows)
        assertThat(list.thumb.top, equalTo(0))

        val bottoms = list.scrollToEndCollecting { list.thumb.bottom }

        assertThat(bottoms.last(), equalTo(list.trackHeight))
    }

    @Test
    fun `thumb never moves against the scroll`() {
        val list = ScrollbarTestList(shortThenTallRows)

        val topsScrollingDown = list.scrollToEndCollecting { list.thumb.top }
        val topsScrollingUp = list.scrollToStartCollecting { list.thumb.top }

        assertThat(topsScrollingDown, equalTo(topsScrollingDown.sorted()))
        assertThat(topsScrollingUp, equalTo(topsScrollingUp.sortedDescending()))
        assertThat(topsScrollingUp.last(), equalTo(0))
    }

    @Test
    fun `thumb carries on from where the list jumped to`() {
        val list = ScrollbarTestList(shortThenTallRows)
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        list.jumpToLastRow()
        list.scrollBy(-SHORT_ROW)

        assertThat(list.thumb.bottom.toDouble(), closeTo(list.trackHeight.toDouble(), list.trackHeight * 0.05))
    }

    @Test
    fun `refreshing rows without changing their heights keeps the thumb size`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        list.refreshRows()

        assertThat(list.thumb.height, equalTo(initialSize))
    }

    @Test
    fun `scrolling right after a row off screen changes keeps the thumb size`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        list.notifyRowChanged(shortThenTallRows.lastIndex)
        list.scrollBy(DISTANCE_TO_TALL_ROWS)

        assertThat(list.thumb.height, equalTo(initialSize))
    }

    @Test
    fun `rows changing height resize the thumb`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        list.refreshRows(List(shortThenTallRows.size) { SHORT_ROW / 2 })

        assertThat(list.thumb.height, not(equalTo(initialSize)))
    }

    @Test
    fun `a different number of rows resizes the thumb`() {
        val list = ScrollbarTestList(shortThenTallRows)
        val initialSize = list.thumb.height

        list.refreshRows(shortThenTallRows.take(40))

        assertThat(list.thumb.height, not(equalTo(initialSize)))
    }

    @Test
    fun `two row list retains its exact range at the end`() {
        val rows = listOf(100, 5000)
        val list = ScrollbarTestList(rows)
        list.scrollBy(rows.sum() - 2000 - 1)
        val heightBeforeEnd = list.thumb.height
        list.scrollBy(1)
        assertThat("endpoint shrank the thumb", list.thumb.height, equalTo(heightBeforeEnd))
    }
}

private const val SHORT_ROW = 100
private const val TALL_ROW = 400
private const val DISTANCE_TO_TALL_ROWS = 30 * SHORT_ROW + 5 * TALL_ROW

private val shortThenTallRows = List(30) { SHORT_ROW } + List(30) { TALL_ROW } + List(30) { SHORT_ROW }

private class ScrollbarTestList(
    rowHeights: List<Int>,
    private val attachBeforeAdapter: Boolean = false,
) {
    private var viewportHeight = HEIGHT
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val adapter = ScrollbarTestRowsAdapter(rowHeights)
    private val recyclerView =
        RecyclerView(context).also {
            it.layoutManager = StableScrollbarLayoutManager(context)
            if (!attachBeforeAdapter) it.adapter = adapter
        }
    private val scroller = RecyclerFastScroller(context)
    private val root =
        FrameLayout(context).apply {
            addView(recyclerView, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(scroller, FrameLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT, Gravity.END))
        }

    val visibleAnchor: Pair<Int, Int>
        get() {
            val lm = recyclerView.layoutManager as StableScrollbarLayoutManager
            val position = lm.findFirstVisibleItemPosition()
            return position to lm.getDecoratedTop(checkNotNull(lm.findViewByPosition(position)))
        }

    val scrollRange: Int get() = recyclerView.computeVerticalScrollRange()
    val thumb: View get() = scroller.handle
    val trackHeight: Int get() = scroller.height - scroller.handleBottomInset

    init {
        scroller.attachRecyclerView(recyclerView)
        if (attachBeforeAdapter) recyclerView.adapter = adapter
        layout()
    }

    fun scrollBy(dy: Int) {
        recyclerView.scrollBy(0, dy)
        layout()
    }

    fun <T> scrollToEndCollecting(value: () -> T): List<T> {
        val values = mutableListOf<T>()
        while (recyclerView.canScrollVertically(1)) {
            scrollBy(SCROLL_STEP)
            values.add(value())
        }
        return values
    }

    fun <T> scrollToStartCollecting(value: () -> T): List<T> {
        val values = mutableListOf<T>()
        while (recyclerView.canScrollVertically(-1)) {
            scrollBy(-SCROLL_STEP)
            values.add(value())
        }
        return values
    }

    fun jumpToLastRow() {
        recyclerView.scrollToPosition(adapter.itemCount - 1)
        scroller.requestLayout()
        layout()
    }

    fun notifyRowChanged(position: Int) = adapter.notifyItemChanged(position)

    fun jumpToRow(
        position: Int,
        offset: Int,
    ) {
        (recyclerView.layoutManager as StableScrollbarLayoutManager).scrollToPositionWithOffset(position, offset)
        scroller.requestLayout()
        layout()
    }

    fun updateRowHeights(
        rowHeights: List<Int>,
        position: Int,
    ) {
        adapter.rowHeights = rowHeights
        adapter.notifyItemChanged(position)
        layout()
    }

    fun refreshAndReanchor(
        rowHeights: List<Int>,
        changedPosition: Int? = null,
    ) {
        val lm = recyclerView.layoutManager as StableScrollbarLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val top = lm.findViewByPosition(first)!!.top
        adapter.rowHeights = rowHeights
        if (changedPosition == null) {
            adapter.notifyDataSetChanged()
        } else {
            adapter.notifyItemChanged(changedPosition)
        }
        lm.scrollToPositionWithOffset(first, top)
        scroller.requestLayout()
        layout()
    }

    fun refreshFirstVisibleRow() {
        val lm = recyclerView.layoutManager as StableScrollbarLayoutManager
        adapter.notifyItemChanged(lm.findFirstVisibleItemPosition())
        scroller.requestLayout()
        layout()
    }

    fun reanchor() {
        val lm = recyclerView.layoutManager as StableScrollbarLayoutManager
        val first = lm.findFirstVisibleItemPosition()
        val top = lm.findViewByPosition(first)!!.top
        lm.scrollToPositionWithOffset(first, top)
        scroller.requestLayout()
        layout()
    }

    fun refreshRows(rowHeights: List<Int> = adapter.rowHeights) {
        adapter.rowHeights = rowHeights
        adapter.notifyDataSetChanged()
        layout()
    }

    fun resizeViewportHeight(height: Int) {
        viewportHeight = height
        scroller.requestLayout()
        layout()
    }

    fun applyBottomPadding(padding: Int) {
        recyclerView.clipToPadding = false
        recyclerView.setPadding(0, 0, 0, padding)
        scroller.handleBottomInset = padding
        scroller.requestLayout()
        layout()
    }

    private fun layout() {
        root.measure(
            MeasureSpec.makeMeasureSpec(WIDTH, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(viewportHeight, MeasureSpec.EXACTLY),
        )
        root.layout(0, 0, WIDTH, viewportHeight)
    }

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 2000
        const val SCROLL_STEP = 50
    }
}

private class ScrollbarTestRowsAdapter(
    var rowHeights: List<Int>,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int,
    ) = object : RecyclerView.ViewHolder(View(parent.context)) {}

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder,
        position: Int,
    ) {
        holder.itemView.layoutParams = RecyclerView.LayoutParams(MATCH_PARENT, rowHeights[position])
    }

    override fun getItemCount() = rowHeights.size
}
