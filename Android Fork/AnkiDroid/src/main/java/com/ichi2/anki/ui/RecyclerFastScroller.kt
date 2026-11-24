/*
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * This file incorporates code under the following license
 *
 *     Copyright 2016 Daniel Ciao
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 *
 *  https://github.com/pluscubed/recycler-fast-scroll/blob/3de76812553a77bfd25d3aea0a0af4d96516c3e3/library/src/main/java/com/pluscubed/recyclerfastscroll/RecyclerFastScroller.java
 *
 * CHANGES:
 * * Converted Java to Kotlin
 * * Removed Hungarian notation
 * * Add attachFastScroller method
 * * Reduced variable access
 * * converted hideDelay to time.Duration
 * * removed styleable elements and went with defaults (colorControlNormal)
 * * inlined a number of variables set in init { }
 */

package com.ichi2.anki.ui
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.StateListDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.IdRes
import androidx.annotation.VisibleForTesting
import androidx.core.graphics.drawable.toDrawable
import androidx.core.view.GravityCompat
import androidx.interpolator.view.animation.FastOutLinearInInterpolator
import androidx.interpolator.view.animation.LinearOutSlowInInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors
import com.ichi2.anki.common.utils.dp
import com.ichi2.anki.utils.ext.wholeAndFraction
import com.ichi2.anki.utils.postDelayed
import com.ichi2.utils.isRtl
import timber.log.Timber
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class RecyclerFastScroller
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : FrameLayout(context, attrs, defStyleAttr) {
        @VisibleForTesting
        internal val bar: View

        @VisibleForTesting
        internal val handle: View
        val hiddenTranslationX: Int
        private val hide: Runnable
        private val minScrollHandleHeight: Int = 48.dp.toPx(context)
        var onHandleTouchListener: OnTouchListener? = null

        private var appBarLayoutOffset: Int = 0

        /**
         * Inset, in pixels, reserved at the bottom of the handle's travel.
         *
         * For rounded display corners with edge to edge support.
         *
         * The handle is constrained to `height - handleBottomInset`, so it stays touchable and its
         * bottom aligns with the list's last item at full scroll.
         *
         * The track is drawn edge-to-edge while scrolling and its bottom retracts to the same
         * positions as the last row of the content when approaching the end.
         */
        var handleBottomInset: Int = 0
            set(value) {
                if (field == value) return
                field = value
                requestLayout()
            }

        private var recyclerView: RecyclerView? = null

        private var animator: AnimatorSet? = null
        private var animatingIn: Boolean = false

        /**
         * the delay in millis to hide the scrollbar
         */
        private var hideDelay: Duration = DEFAULT_AUTO_HIDE_DELAY
        private var handleNormalColor: Int =
            MaterialColors.getColor(context, android.R.attr.colorControlNormal, 0)
        private var handlePressedColor: Int =
            MaterialColors.getColor(context, android.R.attr.colorAccent, 0)
        private var barColor: Int =
            MaterialColors.getColor(context, android.R.attr.colorControlNormal, 0)
        private var barInset = 0

        private var hideOverride = false
        private var adapter: RecyclerView.Adapter<*>? = null
        private val adapterObserver: RecyclerView.AdapterDataObserver =
            object : RecyclerView.AdapterDataObserver() {
                override fun onChanged() {
                    super.onChanged()
                    requestLayout()
                }
            }

        /**
         * @throws RuntimeException if set to more than 48dp
         */
        var touchTargetWidth: Int = 24.dp.toPx(context)
            set(touchTargetWidth) {
                field = touchTargetWidth

                val eightDp: Int = 8.dp.toPx(context)
                barInset = touchTargetWidth - eightDp

                val fortyEightDp: Int = 48.dp.toPx(context)
                if (touchTargetWidth > fortyEightDp) {
                    throw RuntimeException("Touch target width cannot be larger than 48dp!")
                }

                bar.layoutParams =
                    LayoutParams(
                        touchTargetWidth,
                        LayoutParams.MATCH_PARENT,
                        GravityCompat.END,
                    )
                handle.layoutParams =
                    LayoutParams(
                        touchTargetWidth,
                        LayoutParams.MATCH_PARENT,
                        GravityCompat.END,
                    )

                updateHandleColorsAndInset()
                updateBarColorAndInset()
            }

        init {

            layoutParams = LayoutParams(minScrollHandleHeight, LayoutParams.MATCH_PARENT)

            bar = View(context)
            handle = View(context)
            addView(bar)
            addView(handle)

            // execute the setter logic
            touchTargetWidth = 24.dp.toPx(context)

            val eightDp: Int = 8.dp.toPx(context)
            hiddenTranslationX =
                (if (isRtl()) -1 else 1) * eightDp
            hide =
                Runnable {
                    if (!handle.isPressed) {
                        if (animator != null && animator!!.isStarted) {
                            animator!!.cancel()
                        }
                        animator = AnimatorSet()
                        val animator2 =
                            ObjectAnimator.ofFloat(
                                this@RecyclerFastScroller,
                                TRANSLATION_X,
                                hiddenTranslationX.toFloat(),
                            )
                        animator2.interpolator = FastOutLinearInInterpolator()
                        animator2.setDuration(150)
                        handle.isEnabled = false
                        animator!!.play(animator2)
                        animator!!.start()
                    }
                }
            translationX = hiddenTranslationX.toFloat()
        }

        /**
         * whether hiding is enabled
         */
        var isHidingEnabled: Boolean = true
            set(hidingEnabled) {
                field = hidingEnabled
                if (hidingEnabled) {
                    postAutoHide()
                }
            }

        private fun updateHandleColorsAndInset() {
            val drawable = StateListDrawable()

            if (!isRtl()) {
                drawable.addState(
                    PRESSED_ENABLED_STATE_SET,
                    InsetDrawable(handlePressedColor.toDrawable(), barInset, 0, 0, 0),
                )
                drawable.addState(
                    EMPTY_STATE_SET,
                    InsetDrawable(handleNormalColor.toDrawable(), barInset, 0, 0, 0),
                )
            } else {
                drawable.addState(
                    PRESSED_ENABLED_STATE_SET,
                    InsetDrawable(handlePressedColor.toDrawable(), 0, 0, barInset, 0),
                )
                drawable.addState(
                    EMPTY_STATE_SET,
                    InsetDrawable(handleNormalColor.toDrawable(), 0, 0, barInset, 0),
                )
            }
            handle.background = drawable
        }

        private fun updateBarColorAndInset() {
            val drawable: Drawable =
                if (!isRtl()) {
                    InsetDrawable(barColor.toDrawable(), barInset, 0, 0, 0)
                } else {
                    InsetDrawable(barColor.toDrawable(), 0, 0, barInset, 0)
                }
            drawable.alpha = 57
            bar.background = drawable
        }

        fun attachRecyclerView(recyclerView: RecyclerView) {
            this.recyclerView = recyclerView
            this.recyclerView!!.addOnScrollListener(
                object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(
                        recyclerView: RecyclerView,
                        dx: Int,
                        dy: Int,
                    ) {
                        super.onScrolled(recyclerView, dx, dy)
                        this@RecyclerFastScroller.show(true)
                    }
                },
            )
            if (recyclerView.adapter != null) attachAdapter(recyclerView.adapter)
        }

        private fun attachAdapter(adapter: RecyclerView.Adapter<*>?) {
            if (this.adapter === adapter) return
            this.adapter?.unregisterAdapterDataObserver(adapterObserver)
            adapter?.registerAdapterDataObserver(adapterObserver)
            this.adapter = adapter
        }

        /**
         * Show the fast scroller and hide after delay
         *
         * @param animate whether to animate showing the scroller
         */
        fun show(animate: Boolean) {
            requestLayout()

            post(
                Runnable {
                    if (hideOverride) {
                        return@Runnable
                    }
                    handle.isEnabled = true
                    if (animate) {
                        if (!animatingIn && translationX != 0f) {
                            if (animator != null && animator!!.isStarted) {
                                animator!!.cancel()
                            }
                            animator = AnimatorSet()
                            val animator =
                                ObjectAnimator.ofFloat(this@RecyclerFastScroller, TRANSLATION_X, 0f)
                            animator.interpolator = LinearOutSlowInInterpolator()
                            animator.setDuration(100)
                            animator.addListener(
                                object : AnimatorListenerAdapter() {
                                    override fun onAnimationEnd(animation: Animator) {
                                        super.onAnimationEnd(animation)
                                        animatingIn = false
                                    }
                                },
                            )
                            animatingIn = true
                            this.animator!!.play(animator)
                            this.animator!!.start()
                        }
                    } else {
                        translationX = 0f
                    }
                    postAutoHide()
                },
            )
        }

        fun postAutoHide() {
            if (!isHidingEnabled) return
            recyclerView?.apply {
                removeCallbacks(hide)
                postDelayed(hide, hideDelay)
            }
        }

        /**
         * The current scroll progress as a value between 0.0 and 1.0.
         */
        private var pendingScrollProportion = 0f

        // Task that converts handle position into scroll command
        private val scrollTask =
            Runnable {
                val lm = recyclerView?.layoutManager as? LinearLayoutManager ?: return@Runnable
                val adapter = recyclerView?.adapter ?: return@Runnable

                try {
                    // Calculate the exact target including the decimal
                    val (targetIndex, fraction) = (pendingScrollProportion.toDouble() * adapter.itemCount).wholeAndFraction()
                    // Estimate height using the first visible view, this is a heuristic
                    val estimatedHeight = recyclerView?.getChildAt(0)?.height ?: 0

                    // Calculate the offset by pushing the item up by the fraction of its height
                    // e.g. If at 99.9%, push the last card up by 90% of its height so we can see the bottom.
                    val offset = -(fraction * estimatedHeight).toInt()
                    lm.scrollToPositionWithOffset(targetIndex.toInt(), offset)
                } catch (e: Exception) {
                    Timber.w(e, "scrollToPosition")
                }
            }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            return when (event.actionMasked) {
                MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                    // Retrieve the adapter to determine item count.
                    val adapter = recyclerView?.adapter ?: return false

                    if (adapter.itemCount == 0) return false

                    // Force the handle to be selected since the user is touching the track (the parent container) and not the handle itself.
                    handle.isPressed = true

                    // The valid scroll area is (usable track - handle.height), since the position of the handle is defined by its top edge, we subtract it.
                    // The usable track excludes handleBottomInset (nav bar) so the handle can't be dragged into it.
                    val scrollableHeight = (height - handleBottomInset) - handle.height

                    // Subtract half the handle's height and divide by 2 so the handle centers below the user's finger instead of hanging above or below.
                    // Divide by the scrollableHeight to make sure that the handle doesn't go off the screen and we use coerceAtLeast to prevent divide by 0 errors
                    val scrollProportion =
                        ((event.y - handle.height / 2) / scrollableHeight.coerceAtLeast(1))
                            .coerceIn(0f, 1f)
                    pendingScrollProportion = scrollProportion
                    // Calculates the item index we want to go to by multiplying our ScrollProportion to the item count
                    // e.g. if we are going to 50% then 0.5*itemcount gives us the index we need.
                    // toInt prevents decimal values, and coerceIn here makes it so when we scroll all the way to the end, we don't get an out of bounds error.
                    val targetPosition =
                        (scrollProportion * adapter.itemCount)
                            .toInt()
                            .coerceIn(0, adapter.itemCount - 1)

                    try {
                        (recyclerView?.layoutManager as? LinearLayoutManager)
                            ?.scrollToPositionWithOffset(targetPosition, 0)
                            ?: recyclerView?.scrollToPosition(targetPosition)
                    } catch (e: Exception) {
                        Timber.w(e, "scrollToPosition")
                    }

                    // destroys any redundant calls to the scrolltask and sets a small delay to improve performance
                    recyclerView?.removeCallbacks(scrollTask)
                    recyclerView?.postDelayed(scrollTask, 20.milliseconds)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handle.isPressed = false
                    if (recyclerView != null) {
                        recyclerView?.let { removeCallbacks(scrollTask) }
                        scrollTask.run()
                    }
                    postAutoHide()
                    false
                }
                else -> super.onTouchEvent(event)
            }
        }

        override fun onLayout(
            changed: Boolean,
            left: Int,
            top: Int,
            right: Int,
            bottom: Int,
        ) {
            super.onLayout(changed, left, top, right, bottom)
            if (recyclerView == null) return
            attachAdapter(recyclerView!!.adapter)

            val scrollOffset = recyclerView!!.computeVerticalScrollOffset() + appBarLayoutOffset
            // The content range and the visible viewport come from the RecyclerView itself.
            // Sizing & positioning the handle against the viewport (rather than the track height)
            // keeps it proportional even when the track is shortened to clear the navigation bar,
            // so the handle reaches the bottom of the track exactly when the list reaches its end.
            val verticalScrollRange = recyclerView!!.computeVerticalScrollRange()
            val verticalScrollExtent = recyclerView!!.computeVerticalScrollExtent()

            // The track (bar) spans the full height, but the handle travels only the area above
            // handleBottomInset so it stays clear of the navigation bar / rounded corner.
            val fullBarHeight = bar.height
            val trackHeight = (fullBarHeight - handleBottomInset).coerceAtLeast(0)
            val maxScrollOffset = (verticalScrollRange - verticalScrollExtent).coerceAtLeast(1)
            val ratio = (scrollOffset.toFloat() / maxScrollOffset).coerceIn(0f, 1f)

            var calculatedHandleHeight = (trackHeight.toFloat() * verticalScrollExtent / verticalScrollRange).toInt()
            if (calculatedHandleHeight < minScrollHandleHeight) {
                calculatedHandleHeight = minScrollHandleHeight
            }

            if (calculatedHandleHeight >= trackHeight) {
                translationX = hiddenTranslationX.toFloat()
                hideOverride = true
                return
            }

            hideOverride = false

            val y = ratio * (trackHeight - calculatedHandleHeight)

            handle.layout(handle.left, y.toInt(), handle.right, y.toInt() + calculatedHandleHeight)

            // The track is edge-to-edge (drawn under the navigation bar) while the bottom of the last
            // row is below the viewport. Once that bottom scrolls into view the track follows it
            // exactly, so they stay aligned.
            val layoutManager = recyclerView!!.layoutManager
            val lastPosition = (recyclerView!!.adapter?.itemCount ?: 0) - 1
            // The last row's bottom comes from its real on-screen position
            // Note: Offsets can't be used - LinearLayoutManager reports them in scrollbar units
            val lastRowBottomEdge =
                lastPosition
                    .takeIf { it >= 0 }
                    ?.let { layoutManager?.findViewByPosition(it) }
                    ?.let { layoutManager!!.getDecoratedBottom(it) }

            // off-screen (or not laid out) → edge-to-edge; visible → follow the row's bottom
            val isEdgeToEdge = lastRowBottomEdge == null || lastRowBottomEdge >= fullBarHeight
            val barBottom = bar.top + if (isEdgeToEdge) fullBarHeight else lastRowBottomEdge
            if (bar.bottom != barBottom) {
                bar.layout(bar.left, bar.top, bar.right, barBottom)
            }
        }

        companion object {
            private val DEFAULT_AUTO_HIDE_DELAY = 1500.milliseconds
        }
    }

fun RecyclerView.attachFastScroller(
    @IdRes id: Int,
) = (parent as ViewGroup)
    .findViewById<RecyclerFastScroller>(id)
    .attachRecyclerView(this)
