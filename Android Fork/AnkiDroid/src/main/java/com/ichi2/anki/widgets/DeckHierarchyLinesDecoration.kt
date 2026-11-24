// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2026 Shaan Narendran <shaannaren06@gmail.com>
package com.ichi2.anki.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.appcompat.widget.ThemeUtils
import androidx.core.view.children
import androidx.recyclerview.widget.RecyclerView
import com.ichi2.anki.common.utils.android.systemIsInNightMode
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.common.android.R as CommonR

@JvmInline
private value class DepthBitSet(
    val value: Long = 0L,
) {
    fun setBit(depth: Int): DepthBitSet {
        if (depth >= 64) return this
        return DepthBitSet(value or (1L shl depth))
    }

    fun clearDeeperThan(depth: Int): DepthBitSet {
        if (depth >= 64) return this
        val mask = if (depth >= 63) -1L else (1L shl (depth + 1)) - 1L
        return DepthBitSet(value and mask)
    }

    fun hasBit(depth: Int): Boolean {
        if (depth >= 64) return false
        return (value and (1L shl depth)) != 0L
    }
}

class DeckHierarchyLinesDecoration(
    context: Context,
    private val adapter: DeckAdapter,
) : RecyclerView.ItemDecoration() {
    private val paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.BUTT
            strokeWidth = LINE_STROKE_WIDTH_DP.dp.toPx(context).toFloat()

            color = ThemeUtils.getThemeAttrColor(context, com.google.android.material.R.attr.colorOnSurface)
        }

    private val targetAlpha = if (systemIsInNightMode(context)) ALPHA_DARK else ALPHA_LIGHT
    private val layerPaint = Paint().apply { alpha = targetAlpha }

    // The horizontal indent spacing between each depth level
    private val nestedIndent = context.resources.getDimension(CommonR.dimen.keyline_1)

    // The offset to center the vertical lines with the expander chevron
    private val expanderCenterOffset = adapter.expanderWidth / 2f

    // The radius for the curved elbow when drawing leaf node L-branches
    private val cornerRadius = 8.dp.toPx(context).toFloat()

    // The gap between the end of the line and the expander chevron/deck name
    private val iconGap = 8.dp.toPx(context).toFloat()

    // Reusable path and rect for drawing the curved elbows to avoid allocations
    private val reusablePath = Path()
    private val reusableRect = RectF()

    /**
     * Scratch buffer used to store the bitmask of active vertical lines passing through each visible row.
     * The index maps to the view's index in the sorted list of visible views, and the 64-bit Long stores the active depths.
     * It dynamically resizes if the RecyclerView holds more visible items.
     */
    private var scratchBuffer = LongArray(50)

    override fun onDrawOver(
        c: Canvas,
        parent: RecyclerView,
        state: RecyclerView.State,
    ) {
        val childCount = parent.childCount
        if (childCount == 0) return

        val currentList = adapter.currentList
        if (currentList.isEmpty()) return

        if (scratchBuffer.size < childCount) {
            scratchBuffer = LongArray(childCount * 2)
        }
        // We use a 64-bit Long as a bitmask. Each bit index represents a depth level.
        // If bit 'd' is 1, it means there is a sibling further down the list at depth 'd'
        // so we must draw a vertical line
        var activeLines = DepthBitSet()

        // Gather visible views and sort them by adapter position to ensure we process them top-to-bottom.
        // RecyclerView.getChildAt() order is not guaranteed to match adapter position order, especially during animations.
        // The deck adapter may be wrapped (e.g. in a ConcatAdapter), so only deck rows are used,
        // indexed by their position in the deck adapter.
        val visibleViews =
            parent.children
                .map { parent.getChildViewHolder(it) to it }
                .filter { (holder, _) -> holder.bindingAdapter === adapter }
                .map { (holder, view) -> holder.bindingAdapterPosition to view }
                .filter { (pos, _) -> pos != RecyclerView.NO_POSITION }
                .sortedBy { (pos, _) -> pos }
                .toList()

        if (visibleViews.isEmpty()) return

        val maxPos = visibleViews.last().first

        // Look ahead past the last visible item to find lines that continue below the screen
        if (maxPos + 1 < currentList.size) {
            val endPos = currentList.size
            var minDepthSeen = (currentList.getOrNull(maxPos)?.depth ?: 63) + 1
            for (i in maxPos + 1 until endPos) {
                val node = currentList.getOrNull(i) ?: continue
                val d = node.depth
                if (d >= 64) {
                    continue
                }

                // We only care about finding the first sibling for each depth <= maxPos.depth.
                // Since deeper nodes are children of shallower nodes, a shallower node terminates
                // all lines for deeper depths. Thus, we only set a bit if we find a depth that is
                // strictly shallower than the shallowest depth we've seen so far in our look-ahead.
                if (d < minDepthSeen) {
                    minDepthSeen = d
                    activeLines = activeLines.setBit(d)
                    if (d == 0) break // Root node resets everything below it
                }
            }
        }

        // Scan backwards over the sorted visible items to record the active lines after each node
        for (j in visibleViews.indices.reversed()) {
            val (pos, view) = visibleViews[j]
            val node = currentList.getOrNull(pos)
            if (node == null) {
                scratchBuffer[j] = 0L
                continue
            }

            // We store the bitmask we made earlier in the scratch buffer for each row so that we can
            // know which lines need to pass through this row to reach the decks after it
            scratchBuffer[j] = activeLines.value

            // We look at the last deck that we can see and store it in d
            // eg:- if we have a child at the bottom of depth 2 our bits look like 0100
            val d = node.depth
            if (d < 64) {
                // We do an or with the activeLines (activelines stores the number of lines passing through
                // the current row) and this will give us the number of lines to draw
                activeLines = activeLines.setBit(d).clearDeeperThan(d)
            }
        }

        // Get the ItemAnimator to help compute drawing parameters during animations
        val isAnimating = parent.itemAnimator?.isRunning ?: false

        // Create a layer to prevent alpha compounding of overlapping lines during animations
        val saveCount = c.saveLayer(0f, 0f, parent.width.toFloat(), parent.height.toFloat(), layerPaint)

        // Loop to draw the lines
        for (j in visibleViews.indices) {
            val (position, view) = visibleViews[j]
            val node = currentList.getOrNull(position)
            if (node == null) continue

            val nextNode = currentList.getOrNull(position + 1)
            drawLinesForView(
                c = c,
                view = view,
                node = node,
                nextNode = nextNode,
                siblingMask = scratchBuffer[j],
                isAnimating = isAnimating,
            )
        }

        c.restoreToCount(saveCount)
    }

    private fun drawLinesForView(
        c: Canvas,
        view: android.view.View,
        node: com.ichi2.anki.deckpicker.DisplayDeckNode,
        nextNode: com.ichi2.anki.deckpicker.DisplayDeckNode?,
        siblingMask: Long,
        isAnimating: Boolean,
    ) {
        val viewAlpha = view.alpha
        // Hide lines entirely for views that are collapsing/disappearing
        if (viewAlpha <= 0f) return

        val context = view.context
        // Apply a smooth fade out to lines when the item shrinks vertically
        val heightRatio =
            if (isAnimating && view.height < 48.dp.toPx(context)) {
                view.height / 48.dp.toPx(context).toFloat()
            } else {
                1f
            }
        paint.alpha = (255 * viewAlpha * heightRatio).toInt().coerceIn(0, 255)

        val depth = node.depth

        // Handle vertical animation offsets
        val translationY = view.translationY
        val top = view.top + translationY
        val bottom = view.bottom + translationY
        val centerY = top + view.height / 2f

        val bitSet = DepthBitSet(siblingMask)
        // Helper to check the precomputed bitmask
        val hasSibling = { targetDepth: Int ->
            bitSet.hasBit(targetDepth)
        }

        for (level in 0 until depth - 1) {
            if (hasSibling(level + 1)) {
                val x = getLineX(level)
                c.drawLine(x, top, x, bottom, paint)
            }
        }

        if (depth > 0) {
            val level = depth - 1
            val x = getLineX(level)
            val childCenterX = getLineX(depth)
            // If the node has no children (is a leaf), the line should exactly match the visible
            // termination point of the lines pointing to sibling chevrons.
            val horizontalExtension = childCenterX - iconGap
            val endX = maxOf(x + cornerRadius, horizontalExtension)

            if (hasSibling(depth)) {
                // Draw vertical line as top and bottom segments to avoid overlap at the T-junction
                // which causes darker color from alpha overdraw
                val strokeHalf = paint.strokeWidth / 2f
                c.drawLine(x, top, x, centerY - strokeHalf, paint)
                // If the view is animating out and shrinking, we shouldn't draw the bottom vertical stroke
                // extending into empty space, as it causes a momentary visual glitch grid
                if (!isAnimating || view.height >= 48.dp.toPx(context)) {
                    c.drawLine(x, centerY + strokeHalf, x, bottom, paint)
                }
                // Draw horizontal line ending right at the vertical stroke's edge
                c.drawLine(x - strokeHalf, centerY, endX, centerY, paint)
            } else {
                reusablePath.reset()
                reusablePath.moveTo(x, top)
                reusablePath.lineTo(x, centerY - cornerRadius)
                reusableRect.set(x, centerY - 2 * cornerRadius, x + 2 * cornerRadius, centerY)
                reusablePath.arcTo(reusableRect, 180f, -90f, false)
                reusablePath.lineTo(endX, centerY)
                c.drawPath(reusablePath, paint)
            }
        }

        // The vertical line originating beneath the chevron must be anchored to the chevron's
        // Y-coordinate center + offset instead of the view's center, since padding/layout might
        // cause the chevron to not be perfectly centered relative to the view bounds
        if (nextNode != null && nextNode.depth == depth + 1 && (!isAnimating || view.height >= 48.dp.toPx(context))) {
            val x = getLineX(depth)
            val chevronBottomY = centerY + iconGap
            c.drawLine(x, chevronBottomY, x, bottom, paint)
        }
    }

    private fun getLineX(depth: Int): Float =
        depth * nestedIndent +
            expanderCenterOffset +
            if (depth == 0) 0f else adapter.nestedExpanderOffset

    companion object {
        private const val LINE_STROKE_WIDTH_DP = 2f
        private const val ALPHA_LIGHT = 30
        private const val ALPHA_DARK = 50
    }
}
