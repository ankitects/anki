/*
 *  Copyright (c) 2024 deysak <deysakos@gmail.com>
 *
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
 *
 *  This file incorporates code under the following license:
 *
 *   Copyright (C) 2015 The Android Open Source Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 *  https://cs.android.com/androidx/platform/frameworks/support/+/5c27b70c8541f02542fca57734ac8d3be6db45a5:core/core/src/main/java/androidx/core/widget/NestedScrollView.java
 */

package com.ichi2.anki.workarounds

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import androidx.core.view.NestedScrollingChild
import androidx.core.view.NestedScrollingChildHelper
import androidx.core.view.ViewCompat
import androidx.core.view.ViewCompat.ScrollAxis
import androidx.core.widget.NestedScrollView

/**
 * NestedScrollingWebView is-a [WebView] that implements [NestedScrollingChild].
 *
 * It is useful for purposes where [WebView] is required to be:
 *  1. Scrollable
 *  2. Zoomable
 *  3. Supporting nested scrolling
 *
 * **Example:** Hiding the toolbar upon scroll
 * ([Issue-14991](https://github.com/ankidroid/Anki-Android/issues/14991)).
 *
 * **Need:**
 * There is no default way to reliably support all the 3 aforementioned behaviors as:
 *  * Using [NestedScrollView] interferes with the zoom
 *  ([Issue-16135](https://github.com/ankidroid/Anki-Android/issues/16135)).
 * * Intercepting the scale motion events from [NestedScrollView]
 * and passing it to [WebView] is unreliable.
 * * Hiding the toolbar by detecting scroll is unreliable.
 * * Workarounds for ensuring reliable zoom behavior interferes with scrolling.
 * * Methods allowing consistent scroll and zoom behavior are not reliable for hiding toolbar.
 *
 * **Usage:**
 * 1. [WebView.setNestedScrollingEnabled] must be set to `true`.
 * 2. XML: `<com.ichi2.anki.workarounds.NestedScrollingWebView ... />`
 */
class NestedScrollingWebView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = android.R.attr.webViewStyle,
    ) : WebView(context, attrs, defStyleAttr),
        NestedScrollingChild {
        private val yAxis: Int = 1

        private val deltaX: Int = 0

    /*
     * https://developer.android.com/reference/androidx/core/view/NestedScrollingChild
     * Classes implementing this interface should create a final instance of
     * a NestedScrollingChildHelper as a field and delegate any View methods to
     * the NestedScrollingChildHelper methods of the same signature.
     */
        private val childHelper: NestedScrollingChildHelper = NestedScrollingChildHelper(this)

        private val scrollOffset: IntArray = IntArray(2)

        private val scrollConsumed: IntArray = IntArray(2)

        private var lastMotionY: Int = 0

        private var nestedYOffset: Int = 0

        override fun onTouchEvent(motionEvent: MotionEvent): Boolean {
            val actionMasked: Int = motionEvent.actionMasked
            if (actionMasked == MotionEvent.ACTION_DOWN) {
                nestedYOffset = 0
            }

            val velocityTrackerMotionEvent: MotionEvent = MotionEvent.obtain(motionEvent)
            handleOffset(velocityTrackerMotionEvent, nestedYOffset)

            val motionEventY: Int = motionEvent.y.toInt()
            when (actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastMotionY = motionEventY
                    startNestedScroll(ViewCompat.SCROLL_AXIS_VERTICAL)
                }

                MotionEvent.ACTION_MOVE -> {
                    val scrollDistanceY: Int = lastMotionY - motionEventY
                    if (dispatchNestedPreScroll(
                            deltaX,
                            scrollDistanceY,
                            scrollConsumed,
                            scrollOffset,
                        )
                    ) {
                        handleOffset(velocityTrackerMotionEvent, scrollOffset[yAxis])
                        nestedYOffset += scrollOffset[yAxis]
                    }
                    lastMotionY = motionEventY - scrollOffset[yAxis]
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL,
                MotionEvent.ACTION_POINTER_DOWN, MotionEvent.ACTION_POINTER_UP,
                -> {
                    stopNestedScroll()
                }
            }

            velocityTrackerMotionEvent.recycle()
            return super.onTouchEvent(velocityTrackerMotionEvent)
        }

        private fun handleOffset(
            velocityTrackerMotionEvent: MotionEvent,
            deltaY: Int,
        ) {
            velocityTrackerMotionEvent.offsetLocation(deltaX.toFloat(), deltaY.toFloat())
        }

        override fun setNestedScrollingEnabled(enabled: Boolean) = childHelper.setNestedScrollingEnabled(enabled)

        override fun isNestedScrollingEnabled() = childHelper.isNestedScrollingEnabled

        override fun startNestedScroll(
            @ScrollAxis axes: Int,
        ) = childHelper.startNestedScroll(axes)

        override fun stopNestedScroll() = childHelper.stopNestedScroll()

        override fun hasNestedScrollingParent() = childHelper.hasNestedScrollingParent()

        override fun dispatchNestedScroll(
            dxConsumed: Int,
            dyConsumed: Int,
            dxUnconsumed: Int,
            dyUnconsumed: Int,
            offsetInWindow: IntArray?,
        ) = childHelper.dispatchNestedScroll(
            dxConsumed,
            dyConsumed,
            dxUnconsumed,
            dyUnconsumed,
            offsetInWindow,
        )

        override fun dispatchNestedPreScroll(
            dx: Int,
            dy: Int,
            consumed: IntArray?,
            offsetInWindow: IntArray?,
        ) = childHelper.dispatchNestedPreScroll(dx, dy, consumed, offsetInWindow)

        override fun dispatchNestedFling(
            velocityX: Float,
            velocityY: Float,
            consumed: Boolean,
        ) = childHelper.dispatchNestedFling(velocityX, velocityY, consumed)

        override fun dispatchNestedPreFling(
            velocityX: Float,
            velocityY: Float,
        ) = childHelper.dispatchNestedPreFling(velocityX, velocityY)
    }

class NestedScrollingSafeWebViewLayout : SafeWebViewLayout {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun createWebView(): NestedScrollingWebView =
        NestedScrollingWebView(context).also {
            it.isNestedScrollingEnabled = true
        }
}
