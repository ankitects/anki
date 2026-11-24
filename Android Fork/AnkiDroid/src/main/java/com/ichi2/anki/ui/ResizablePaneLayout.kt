// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.LinearLayout
import androidx.core.view.children
import androidx.core.view.isVisible
import kotlin.math.abs

class ResizablePaneLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        private val grabDistance = 24 * resources.displayMetrics.density
        private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var nearDivider = false
        private var dragging = false

        private val divider: View?
            get() = children.firstOrNull { it is ResizingDivider && it.isVisible }

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = startsDrag(ev)

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (dragging) {
                divider?.dispatchTouchEvent(event)
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) {
                    dragging = false
                }
                return true
            }
            return startsDrag(event) || nearDivider || super.onTouchEvent(event)
        }

        private fun startsDrag(event: MotionEvent): Boolean {
            val divider = divider ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.x
                    downY = event.y
                    nearDivider = abs(event.x - (divider.left + divider.right) / 2f) <= grabDistance
                    if (nearDivider) parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = abs(event.x - downX)
                    if (nearDivider && dx > touchSlop && dx > abs(event.y - downY)) {
                        nearDivider = false
                        dragging = true
                        val down = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_DOWN }
                        divider.dispatchTouchEvent(down)
                        down.recycle()
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> nearDivider = false
            }
            return false
        }
    }
