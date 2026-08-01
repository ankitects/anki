/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.Context
import android.graphics.Rect
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.marginLeft
import androidx.core.view.marginRight
import androidx.core.view.updateLayoutParams
import androidx.recyclerview.widget.LinearLayoutManager
import com.ichi2.anki.databinding.ViewWhiteboardToolbarBinding
import com.ichi2.anki.utils.ext.setDuration
import com.ichi2.utils.dp
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

/**
 * Tools configuration bar to be used along [WhiteboardView]
 */
class WhiteboardToolbar : LinearLayout {
    private val binding: ViewWhiteboardToolbarBinding
    private val brushAdapter: BrushAdapter
    private val dragHandler = DragHandler()
    private var currentAlignment: ToolbarAlignment = ToolbarAlignment.BOTTOM

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        binding = ViewWhiteboardToolbarBinding.inflate(LayoutInflater.from(context), this)

        brushAdapter =
            BrushAdapter(
                onBrushClick = { view, index -> onBrushClick?.invoke(view, index) },
                onBrushLongClick = { index -> onBrushLongClick?.invoke(index) },
            )

        binding.brushRecyclerView.apply {
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            adapter = brushAdapter
        }
    }

    val undoButton get() = binding.undoButton
    val redoButton get() = binding.redoButton
    val eraserButton get() = binding.eraserButton
    val overflowButton get() = binding.overflowMenuButton

    var onBrushClick: ((view: View, index: Int) -> Unit)? = null
    var onBrushLongClick: ((index: Int) -> Unit)? = null
    var onToolbarVisibilityChanged: ((isShown: Boolean) -> Unit)? = null

    /**
     * Disables gesture swipe of docked whiteboard toolbar on the side on layout change
     * While this code does attempt to set exclusion rect when ToolbarAlignment.BOTTOM,
     *  no effect occurs as bottom screen gestures cannot be opted out of
     * https://developer.android.com/develop/ui/views/touch-and-input/gestures/gesturenav#kotlin
     */
    private val boundingBox: Rect = Rect()
    private val exclusions = listOf(boundingBox)

    override fun onLayout(
        changed: Boolean,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
    ) {
        super.onLayout(changed, left, top, right, bottom)
        if (!changed) return
        when (currentAlignment) {
            ToolbarAlignment.LEFT -> boundingBox.set(left - marginLeft, top, right, bottom)
            ToolbarAlignment.RIGHT -> boundingBox.set(left, top, right + marginRight, bottom)
            ToolbarAlignment.BOTTOM -> boundingBox.set(0, 0, 0, 0)
        }
        ViewCompat.setSystemGestureExclusionRects(this, exclusions)
    }

    /**
     * Updates the internal layout based on the toolbar alignment.
     * Switches the RecyclerView orientation and the main layout orientation.
     */
    fun setAlignment(alignment: ToolbarAlignment) {
        currentAlignment = alignment

        // Check if the toolbar is docked to a side edge (Left/Right).
        val isSideDocked = alignment == ToolbarAlignment.LEFT || alignment == ToolbarAlignment.RIGHT
        binding.innerControlsLayout.orientation = if (isSideDocked) VERTICAL else HORIZONTAL

        val layoutManager = binding.brushRecyclerView.layoutManager as? LinearLayoutManager
        layoutManager?.orientation = if (isSideDocked) LinearLayoutManager.VERTICAL else LinearLayoutManager.HORIZONTAL

        val dp = 1.dp.toPx(context)
        val dividerMargin = 4 * dp
        val dividerParams = binding.controlsDivider.layoutParams as LayoutParams
        if (isSideDocked) {
            dividerParams.width = LayoutParams.MATCH_PARENT
            dividerParams.height = 1 * dp
            dividerParams.setMargins(0, dividerMargin, 0, dividerMargin)
            binding.innerControlsLayout.updateLayoutParams<MarginLayoutParams> {
                marginEnd = 0
            }
        } else {
            dividerParams.width = 1 * dp
            dividerParams.height = LayoutParams.MATCH_PARENT
            dividerParams.setMargins(dividerMargin, 0, dividerMargin, 0)
            binding.innerControlsLayout.updateLayoutParams<MarginLayoutParams> {
                marginEnd = dividerMargin
            }
        }

        // Configure container structure (Handle + Card placement)
        val handleSize = 32.dp.toPx(context)
        removeView(binding.touchHandle)
        removeView(binding.toolbarCard)

        orientation = if (isSideDocked) HORIZONTAL else VERTICAL
        val handleParams =
            if (isSideDocked) {
                LayoutParams(handleSize, LayoutParams.MATCH_PARENT)
            } else {
                LayoutParams(LayoutParams.MATCH_PARENT, handleSize)
            }

        if (alignment == ToolbarAlignment.LEFT) {
            addView(binding.toolbarCard)
            addView(binding.touchHandle, handleParams)
        } else {
            addView(binding.touchHandle, handleParams)
            addView(binding.toolbarCard)
        }
    }

    /**
     * Updates the data in the RecyclerView adapter.
     */
    fun setBrushes(
        brushes: List<BrushInfo>,
        activeIndex: Int,
        isEraserActive: Boolean,
    ) {
        brushAdapter.updateData(brushes, activeIndex, isEraserActive)
    }

    /**
     * Updates the checked state of the brush buttons in the adapter.
     */
    fun updateSelection(
        activeIndex: Int,
        isEraserActive: Boolean,
    ) {
        brushAdapter.updateSelection(activeIndex, isEraserActive)
    }

    /**
     * Animates the toolbar to its hidden (peeking) state.
     */
    fun hide() = dragHandler.hide()

    /**
     * Animates the toolbar to its fully visible state.
     */
    fun show() = dragHandler.show()

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        val intercepted = dragHandler.onInterceptTouchEvent(ev)
        return dragHandler.isHidden || intercepted || super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = dragHandler.onTouchEvent(event) || super.onTouchEvent(event)

    private inner class DragHandler {
        var isHidden = false
            private set

        private var dragStartX = 0f
        private var dragStartY = 0f
        private var initialTranslationX = 0f
        private var initialTranslationY = 0f
        private var isDragging = false
        private val peekSize = 6.dp.toPx(context)
        private val handleSize = 32.dp.toPx(context)
        private val touchSlop by lazy { ViewConfiguration.get(context).scaledTouchSlop }

        private val maxTranslation: Float
            get() {
                val lp = layoutParams as? MarginLayoutParams
                return when (currentAlignment) {
                    ToolbarAlignment.BOTTOM -> height + (lp?.bottomMargin ?: 0) - (peekSize + handleSize)
                    ToolbarAlignment.LEFT -> width + (lp?.leftMargin ?: 0) - (peekSize + handleSize)
                    ToolbarAlignment.RIGHT -> width + (lp?.rightMargin ?: 0) - (peekSize + handleSize)
                }.toFloat()
            }

        fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    dragStartX = ev.rawX
                    dragStartY = ev.rawY
                    initialTranslationX = translationX
                    initialTranslationY = translationY
                    isDragging = false
                    // Let children handle the initial down event (e.g. clicks)
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) return true
                    val dx = ev.rawX - dragStartX
                    val dy = ev.rawY - dragStartY

                    // Intercept if the swipe is perpendicular to the toolbar main axis
                    val shouldIntercept =
                        when (currentAlignment) {
                            ToolbarAlignment.BOTTOM -> abs(dy) > touchSlop && abs(dy) > abs(dx)
                            ToolbarAlignment.LEFT, ToolbarAlignment.RIGHT -> abs(dx) > touchSlop && abs(dx) > abs(dy)
                        }

                    if (shouldIntercept) {
                        isDragging = true
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                }
            }
            return false
        }

        fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> return true
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - dragStartX
                    val dy = event.rawY - dragStartY
                    updateTranslation(dx, dy)
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    isDragging = false
                    snapToNearestState()
                    return true
                }
            }
            return false
        }

        private fun updateTranslation(
            dx: Float,
            dy: Float,
        ) {
            val maxTrans = maxTranslation
            when (currentAlignment) {
                ToolbarAlignment.BOTTOM -> {
                    val newTrans = (initialTranslationY + dy).coerceIn(0f, maxTrans)
                    translationY = newTrans
                }
                ToolbarAlignment.LEFT -> {
                    val newTrans = (initialTranslationX + dx).coerceIn(-maxTrans, 0f)
                    translationX = newTrans
                }
                ToolbarAlignment.RIGHT -> {
                    val newTrans = (initialTranslationX + dx).coerceIn(0f, maxTrans)
                    translationX = newTrans
                }
            }
        }

        fun show() {
            isHidden = false
            val animator = animate().setDuration(200.milliseconds).setInterpolator(DecelerateInterpolator())

            when (currentAlignment) {
                ToolbarAlignment.BOTTOM -> animator.translationY(0f)
                ToolbarAlignment.LEFT, ToolbarAlignment.RIGHT -> animator.translationX(0f)
            }

            animator.start()
            onToolbarVisibilityChanged?.invoke(true)
        }

        fun hide() {
            isHidden = true
            val animator = animate().setDuration(200.milliseconds).setInterpolator(DecelerateInterpolator())
            val maxTrans = maxTranslation

            when (currentAlignment) {
                ToolbarAlignment.BOTTOM -> animator.translationY(maxTrans)
                ToolbarAlignment.LEFT -> animator.translationX(-maxTrans)
                ToolbarAlignment.RIGHT -> animator.translationX(maxTrans)
            }

            animator.start()
            onToolbarVisibilityChanged?.invoke(false)
        }

        /**
         * Hides the toolbar if nearer the edge of the layout than the toolbar original position,
         * which is determined by moving more than >50% of the total distance between the toolbar
         * farthest limit and the layout's edge.
         */
        private fun snapToNearestState() {
            val maxTrans = maxTranslation
            val shouldShow =
                when (currentAlignment) {
                    ToolbarAlignment.BOTTOM -> translationY <= maxTrans / 2
                    ToolbarAlignment.LEFT -> translationX >= -maxTrans / 2
                    ToolbarAlignment.RIGHT -> translationX <= maxTrans / 2
                }

            if (shouldShow) show() else hide()
        }
    }
}
