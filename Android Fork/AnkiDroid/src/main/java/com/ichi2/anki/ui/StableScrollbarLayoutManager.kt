// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.content.Context
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.ceil
import kotlin.math.roundToInt

private typealias RowPosition = Int
private typealias RowHeight = Int
private typealias Pixels = Int

/**
 * A [LinearLayoutManager] whose scrollbar thumb keeps its size while rows of different heights scroll past.
 *
 * It reports one stable range, see [computeVerticalScrollRange], and places the thumb by the rows above
 * and below the screen, see [computeVerticalScrollOffset].
 */
class StableScrollbarLayoutManager(
    context: Context,
) : LinearLayoutManager(context) {
    /**
     * The range given to the scrollbar, and what is known about it.
     * Null before the first layout and after the adapter changes, when the stock estimate is used.
     */
    private var measuredRange: MeasuredRange? = null

    /**
     * The heights of the laid out rows, saved when the adapter reports a change and before it is laid out.
     * [onLayoutCompleted] compares them with the new heights, so a refresh that left every row the same
     * height keeps the range. Null when no change is waiting.
     */
    private var rowHeightsBeforeChange: Map<RowPosition, RowHeight>? = null

    /**
     * How far the list is scrolled: the pixels from the top of the first row to the top of the screen.
     *
     * Each [scrollVerticallyBy] adds to it, and it is read exactly whenever the first row is laid out.
     * At the end of the list it gives the exact range, see [calibratedAtEnd].
     *
     * Null when it can't be known: after a refresh, a change above the screen, or a jump to a row that
     * wasn't on screen. It comes back the next time the first row is laid out.
     */
    private var scrolledFromTop: Pixels? = null

    /**
     * A row on screen and its distance from the top of the list, saved just before a layout and used up
     * when it completes.
     *
     * Jumps ([scrollToPosition], [scrollToPositionWithOffset]) and moves made by the layout itself, such as
     * LinearLayoutManager closing a gap at the end when the bottom padding shrinks, don't go through
     * [scrollVerticallyBy], so [scrolledFromTop] can't count them. Instead, the jump target or the first
     * visible row is saved here before the layout ([rememberAnchor]). Afterwards its new top gives
     * [scrolledFromTop] again ([updateScrolledFromTop]).
     *
     * If that row isn't laid out, before or after the layout, or [scrolledFromTop] isn't known, there is
     * nothing to measure from, so [scrolledFromTop] is cleared instead. A refresh or a change above the screen clears both, because
     * the saved distance no longer holds.
     */
    private var pendingAnchor: Anchor? = null

    /** Remembers the first visible row, so a scroll made by the layout itself is still counted. */
    override fun onLayoutChildren(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ) {
        if (!state.isPreLayout && pendingAnchor == null) rememberAnchor(findFirstVisibleItemPosition())
        super.onLayoutChildren(recycler, state)
    }

    /** Measures the range again when the content changed, then corrects it if the layout changed or moved the list. */
    override fun onLayoutCompleted(state: RecyclerView.State) {
        super.onLayoutCompleted(state)
        val shape = ListShape(state.itemCount, width)
        val rowsChangedHeight = rowHeightsBeforeChange?.let(::anyRowChangedHeight) ?: false
        rowHeightsBeforeChange = null
        val contentChanged = measuredRange?.shape != shape || rowsChangedHeight
        val rangeFitsScreen = measuredRange?.let { it.range - computeVerticalScrollExtent(state) < MIN_SCROLLABLE_PIXELS } ?: false
        val scrolledBefore = scrolledFromTop
        updateScrolledFromTop(contentChanged)
        if (contentChanged || rangeFitsScreen) {
            measuredRange = MeasuredRange(super.computeVerticalScrollRange(state), shape)
        }
        if (contentChanged || rangeFitsScreen || scrolledFromTop != scrolledBefore) {
            measuredRange = measuredRange?.calibratedAtEnd(state)?.loweredToRowsSoFar(state)
        }
    }

    /** Forgets everything measured for the old adapter. */
    override fun onAdapterChanged(
        oldAdapter: RecyclerView.Adapter<*>?,
        newAdapter: RecyclerView.Adapter<*>?,
    ) {
        super.onAdapterChanged(oldAdapter, newAdapter)
        measuredRange = null
        scrolledFromTop = null
        pendingAnchor = null
    }

    /** Keeps counting pixels from the top across a jump to a row that is laid out. */
    override fun scrollToPosition(position: Int) {
        rememberAnchor(position)
        super.scrollToPosition(position)
    }

    /** Keeps counting pixels from the top across a jump to a row that is laid out. */
    override fun scrollToPositionWithOffset(
        position: Int,
        offset: Int,
    ) {
        rememberAnchor(position)
        super.scrollToPositionWithOffset(position, offset)
    }

    /** Counts the pixels scrolled and corrects the range, without moving the thumb back on the way down. */
    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State,
    ): Int {
        val thumbTopBefore = if (dy > 0) thumbTop(state) else null
        val scrolled = super.scrollVerticallyBy(dy, recycler, state)
        scrolledFromTop = scrolledFromTop?.plus(scrolled)
        rememberScrolledFromTop()
        val minRange = thumbTopBefore?.let { rangeMovingThumbForward(it, state) }
        measuredRange = measuredRange?.calibratedAtEnd(state, minRange)
        if (dy > 0) measuredRange = measuredRange?.loweredToRowsSoFar(state, minRange)
        return scrolled
    }

    /** After a full refresh, keeps the range but checks it again at the end. */
    override fun onItemsChanged(recyclerView: RecyclerView) {
        super.onItemsChanged(recyclerView)
        rememberRowHeights()
        measuredRange = measuredRange?.copy(recheck = true, endRange = null, heldUntilTop = false)
        scrolledFromTop = null
        pendingAnchor = null
    }

    /** Checks the range again if an updated row is off screen, and stops counting pixels if one is above the screen. */
    override fun onItemsUpdated(
        recyclerView: RecyclerView,
        positionStart: Int,
        itemCount: Int,
    ) {
        super.onItemsUpdated(recyclerView, positionStart, itemCount)
        rememberRowHeights()
        if ((positionStart until positionStart + itemCount).any { findViewByPosition(it) == null }) {
            measuredRange = measuredRange?.copy(recheck = true, endRange = null, heldUntilTop = false)
        }
        if (positionStart < findFirstVisibleItemPosition()) {
            scrolledFromTop = null
            pendingAnchor = null
        }
    }

    /**
     * Returns the stable range instead of LinearLayoutManager's estimate.
     *
     * The stock estimate is the average height of the rows on screen times the number of rows. It changes
     * whenever taller or shorter rows scroll into view, and the thumb grows and shrinks with it.
     *
     * Here the range is taken from that estimate once, when the content changes, and again if the screen
     * grows to its size ([onLayoutCompleted]). After that it can only be lowered, towards an estimate from
     * every row seen so far ([loweredToRowsSoFar]). When the last row is on screen it is set to the exact
     * value, the pixels scrolled plus the screen ([calibratedAtEnd]). On the way down neither may move the
     * thumb backwards ([rangeMovingThumbForward]), so an exact value that would is held back until a later
     * pass ([MeasuredRange.heldUntilTop]).
     *
     * A refresh, a jump or the keyboard opening keeps the range unless rows really changed height.
     */
    override fun computeVerticalScrollRange(state: RecyclerView.State): Int = stableRange(state) ?: super.computeVerticalScrollRange(state)

    /**
     * Places the thumb by how far down the rows the screen is ([scrollProgress]), not by pixels, so the same
     * rows on screen give the same thumb, and it reaches an end only when the list does.
     *
     * Between the ends the offset stays from 1 to the scrollable pixels minus 2, so
     * [View.canScrollVertically] keeps reporting both directions until the list is really at an end.
     */
    override fun computeVerticalScrollOffset(state: RecyclerView.State): Int {
        val range = stableRange(state) ?: return super.computeVerticalScrollOffset(state)
        val progress = scrollProgress(state.itemCount) ?: return super.computeVerticalScrollOffset(state)
        val scrollable = range - computeVerticalScrollExtent(state)
        return when {
            progress <= 0f -> 0
            progress >= 1f -> scrollable
            else -> (progress * scrollable).roundToInt().coerceIn(1, scrollable - 2)
        }
    }

    /** The thumb top as a fraction of the range. */
    private fun thumbTop(state: RecyclerView.State): Float? {
        val range = computeVerticalScrollRange(state).takeIf { it > 0 } ?: return null
        return computeVerticalScrollOffset(state).toFloat() / range
    }

    /** The smallest range that still moves the thumb forward after a scroll down. */
    private fun rangeMovingThumbForward(
        thumbTopBefore: Float,
        state: RecyclerView.State,
    ): Pixels? {
        val progress = scrollProgress(state.itemCount) ?: return null
        val extent = computeVerticalScrollExtent(state).takeIf { it > 0 } ?: return null
        val thumbTopAfter = thumbTop(state) ?: return null
        val top = thumbTopBefore + ((thumbTopAfter - thumbTopBefore) / 2).coerceIn(0f, 1f / extent)
        return if (top >= progress) Int.MAX_VALUE else ceil(extent / (1 - top / progress)).toInt()
    }

    /** The measured range, or null when there is too little to scroll and the estimate is used. */
    private fun stableRange(state: RecyclerView.State): Pixels? {
        val range = measuredRange?.range ?: return null
        val extent = computeVerticalScrollExtent(state)
        val estimatedRange = super.computeVerticalScrollRange(state)
        return range.takeIf { it - extent >= MIN_SCROLLABLE_PIXELS && estimatedRange - extent >= MIN_SCROLLABLE_PIXELS }
    }

    /** How far down the rows the screen is, from 0 to 1, counting rows cut by the edges in part. */
    private fun scrollProgress(itemCount: Int): Float? {
        val first = findViewByPosition(findFirstVisibleItemPosition()) ?: return null
        val last = findViewByPosition(findLastVisibleItemPosition()) ?: return null
        val rowsAbove = getPosition(first) + fractionOutside(first, paddingTop - getDecoratedTop(first))
        val rowsBelow = itemCount - 1 - getPosition(last) + fractionOutside(last, getDecoratedBottom(last) - (height - paddingBottom))
        if (rowsAbove + rowsBelow <= 0f) return 0f
        return rowsAbove / (rowsAbove + rowsBelow)
    }

    /** The part of [row] outside the screen, from 0 to 1. */
    private fun fractionOutside(
        row: View,
        pixelsOutside: Pixels,
    ) = (pixelsOutside.toFloat() / getDecoratedMeasuredHeight(row).coerceAtLeast(1)).coerceIn(0f, 1f)

    /** Saves how far [position] is from the top so the next layout keeps counting, or stops counting. */
    private fun rememberAnchor(position: RowPosition) {
        val row = findViewByPosition(position)
        pendingAnchor = scrolledFromTop?.let { scrolled -> row?.let { Anchor(position, scrolled + getDecoratedTop(it) - paddingTop) } }
        if (pendingAnchor == null) scrolledFromTop = null
    }

    /** Works out the pixels scrolled from the top after a layout, using the saved anchor if there is one. */
    private fun updateScrolledFromTop(contentChanged: Boolean) {
        val anchor = pendingAnchor
        pendingAnchor = null
        scrolledFromTop =
            when {
                contentChanged -> null
                anchor != null -> findViewByPosition(anchor.position)?.let { anchor.top - (getDecoratedTop(it) - paddingTop) }
                else -> scrolledFromTop
            }
        rememberScrolledFromTop()
    }

    /** Reads the pixels scrolled exactly while the first row is laid out, and releases a held range at the top. */
    private fun rememberScrolledFromTop() {
        findViewByPosition(0)?.let { scrolledFromTop = paddingTop - getDecoratedTop(it) }
        if (scrolledFromTop == 0) measuredRange = measuredRange?.copy(heldUntilTop = false)
    }

    /** At the end, this range set to what was scrolled plus the screen, or held if the thumb would go back. */
    private fun MeasuredRange.calibratedAtEnd(
        state: RecyclerView.State,
        minRange: Pixels? = null,
    ): MeasuredRange {
        val scrolled = scrolledFromTop ?: return this
        if (heldUntilTop || (calibrated && !recheck)) return this
        val lastRow = findViewByPosition(state.itemCount - 1) ?: return this
        if (getDecoratedBottom(lastRow) > height - paddingBottom) return this
        val exact = scrolled + computeVerticalScrollExtent(state)
        val held = minRange?.takeIf { exact < range && exact < it }?.coerceAtMost(range)
        return copy(range = held ?: exact, calibrated = held == null, recheck = false, endRange = exact, heldUntilTop = held != null)
    }

    /** This range lowered towards the end range or the rows seen so far while it is not exact yet, never raised. */
    private fun MeasuredRange.loweredToRowsSoFar(
        state: RecyclerView.State,
        minRange: Pixels? = null,
    ): MeasuredRange {
        if (calibrated || heldUntilTop) return this
        val target = endRange ?: rowsSoFarEstimate(state) ?: return this
        val lowered = maxOf(target, minRange ?: 0)
        return if (lowered < range) copy(range = lowered, calibrated = lowered == endRange) else this
    }

    /** Estimates the range from the rows down to the screen bottom, at least down to the last visible row. */
    private fun rowsSoFarEstimate(state: RecyclerView.State): Pixels? {
        val scrolled = scrolledFromTop ?: return null
        val lastRow = findViewByPosition(findLastVisibleItemPosition()) ?: return null
        val visibleBottom = minOf(getDecoratedBottom(lastRow), height - paddingBottom)
        val rowsSoFar = getPosition(lastRow) + 1 - fractionOutside(lastRow, getDecoratedBottom(lastRow) - visibleBottom)
        val estimate = ((scrolled + visibleBottom - paddingTop) / rowsSoFar * state.itemCount).roundToInt()
        return maxOf(estimate, scrolled + getDecoratedBottom(lastRow) - paddingTop)
    }

    /** Whether a row that is still laid out changed height. */
    private fun anyRowChangedHeight(heightsBefore: Map<RowPosition, RowHeight>) =
        rowHeights().any { (position, height) -> heightsBefore[position].let { it != null && it != height } }

    /** Saves the row heights once, before the change is laid out. */
    private fun rememberRowHeights() {
        if (rowHeightsBeforeChange == null) rowHeightsBeforeChange = rowHeights()
    }

    /** The height of each laid out row, by position. */
    private fun rowHeights(): Map<RowPosition, RowHeight> =
        (0 until childCount)
            .mapNotNull { getChildAt(it) }
            .associate { getPosition(it) to getDecoratedMeasuredHeight(it) }

    /** What a range was measured for. A different row count or width measures it again. */
    private data class ListShape(
        val itemCount: Int,
        val width: Pixels,
    )

    /** The range given to the scrollbar, with what is known about it. */
    private data class MeasuredRange(
        /** The scroll range given to the scrollbar. */
        val range: Pixels,
        /** The row count and width it was measured for. */
        val shape: ListShape,
        /** The range is exact, measured with the last row on screen, so it is no longer lowered. */
        val calibrated: Boolean = false,
        /** Kept after a refresh or an update off screen, but measured again the next time the end is on screen. */
        val recheck: Boolean = false,
        /** The exact range from the last time the end was on screen. A held range is lowered to it later. */
        val endRange: Pixels? = null,
        /** The exact range would have moved the thumb back, so the range stays as it is until the list is back at the top. */
        val heldUntilTop: Boolean = false,
    )

    /** A row on screen saved before a layout, see [pendingAnchor]. */
    private data class Anchor(
        val position: RowPosition,
        /** The pixels from the top of the first row to the top of this row. */
        val top: Pixels,
    )
}

/**
 * The fewest pixels of scrolling for which the stable range is used.
 * [StableScrollbarLayoutManager.computeVerticalScrollOffset] keeps a mid-list offset from 1 to the
 * scrollable pixels minus 2, and that needs at least 3 pixels. With less, LinearLayoutManager's own
 * estimate is used.
 */
private const val MIN_SCROLLABLE_PIXELS: Pixels = 3
