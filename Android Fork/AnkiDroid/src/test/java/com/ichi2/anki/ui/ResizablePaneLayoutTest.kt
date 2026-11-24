// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.content.Context
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.View.MeasureSpec
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.LinearLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ResizablePaneLayoutTest : AndroidTest {
    private val startPane = View(targetContext)
    private val endPane = View(targetContext)
    private val divider = ResizingDivider(targetContext)
    private val layout = createLayout(targetContext)

    private val startWeight get() = (startPane.layoutParams as LinearLayout.LayoutParams).weight
    private val dividerCenter get() = (divider.left + divider.right) / 2f

    @Test
    fun `dragging next to the divider resizes the panes`() {
        startPane.isClickable = true
        drag(fromX = dividerCenter - 16, dx = 100f, dy = 0f)
        assertTrue(startWeight > 1f, "start pane weight: $startWeight")
    }

    @Test
    fun `dragging next to the divider over an empty area resizes the panes`() {
        drag(fromX = dividerCenter + 16, dx = -100f, dy = 0f)
        assertTrue(startWeight < 1f, "start pane weight: $startWeight")
    }

    @Test
    fun `tapping next to the divider still reaches the pane`() {
        val actions = mutableListOf<Int>()
        startPane.setOnTouchListener { _, event ->
            actions += event.actionMasked
            true
        }
        drag(fromX = dividerCenter - 16, dx = 0f, dy = 0f)
        assertEquals(MotionEvent.ACTION_UP, actions.last())
        assertEquals(1f, startWeight)
    }

    @Test
    fun `vertical drags next to the divider do not resize`() {
        startPane.isClickable = true
        drag(fromX = dividerCenter - 16, dx = 0f, dy = 200f)
        assertEquals(1f, startWeight)
    }

    @Test
    fun `drags away from the divider do not resize`() {
        startPane.isClickable = true
        drag(fromX = 100f, dx = 100f, dy = 0f)
        assertEquals(1f, startWeight)
    }

    private fun createLayout(context: Context): LinearLayout =
        ResizablePaneLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(startPane, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            addView(divider, LinearLayout.LayoutParams(8, MATCH_PARENT))
            addView(endPane, LinearLayout.LayoutParams(0, MATCH_PARENT, 1f))
            measure(MeasureSpec.makeMeasureSpec(808, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(600, MeasureSpec.EXACTLY))
            layout(0, 0, 808, 600)
            ResizablePaneManager(
                parentLayout = this,
                divider = divider,
                startPane = startPane,
                endPane = endPane,
                sharedPrefs = context.getSharedPreferences("ResizablePaneLayoutTest", Context.MODE_PRIVATE),
                startPaneWeightKey = "start",
                endPaneWeightKey = "end",
            )
        }

    private fun drag(
        fromX: Float,
        dx: Float,
        dy: Float,
    ) {
        val fromY = 300f
        val downTime = SystemClock.uptimeMillis()
        val steps = 10
        layout.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, fromX, fromY, 0))
        for (step in 1..steps) {
            val x = fromX + dx * step / steps
            val y = fromY + dy * step / steps
            layout.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + step * 10, MotionEvent.ACTION_MOVE, x, y, 0))
        }
        layout.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 200, MotionEvent.ACTION_UP, fromX + dx, fromY + dy, 0))
    }
}
