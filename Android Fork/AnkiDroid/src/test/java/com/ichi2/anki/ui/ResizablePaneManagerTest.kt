// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui

import android.content.Context
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.content.edit
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.FragmentActivity
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.AndroidTest
import com.ichi2.testutils.EmptyApplication
import com.ichi2.testutils.targetContext
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class ResizablePaneManagerTest : AndroidTest {
    private val prefs get() = targetContext.getSharedPreferences("ResizablePaneManagerTest", Context.MODE_PRIVATE)
    private lateinit var startPane: View
    private lateinit var endPane: View
    private lateinit var divider: View

    @Test
    fun `a pane cannot be dragged narrower than 240dp`() {
        createPanes(width = 1008)
        dragDivider(by = 900f)
        assertEquals(240f, endPane.width.toFloat(), absoluteTolerance = 1f)
    }

    @Test
    fun `the minimum width grows with the font size`() {
        RuntimeEnvironment.setFontScale(1.5f)
        createPanes(width = 1008)
        dragDivider(by = 900f)
        assertEquals(360f, endPane.width.toFloat(), absoluteTolerance = 1f)
    }

    @Test
    fun `the divider stays in the middle when two minimums do not fit`() {
        createPanes(width = 408)
        dragDivider(by = 300f)
        assertEquals(startPane.width.toFloat(), endPane.width.toFloat(), absoluteTolerance = 1f)
    }

    @Test
    fun `a saved layout that squeezes a pane opens at the minimum`() {
        prefs.edit {
            putFloat("start", 4.8f)
            putFloat("end", 0.2f)
        }
        createPanes(width = 1008)
        assertEquals(240f, endPane.width.toFloat(), absoluteTolerance = 1f)
    }

    @Test
    fun `a layout with no width yet keeps its saved split`() {
        prefs.edit {
            putFloat("start", 3.5f)
            putFloat("end", 1.5f)
        }
        createPanes(width = 1008, visible = false)
        (startPane.parent as View).isVisible = true
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(300f, endPane.width.toFloat(), absoluteTolerance = 1f)
    }

    @Test
    fun `a pane keeps the minimum when the window gets narrower`() {
        createPanes(width = 1008)
        dragDivider(by = 900f)
        (startPane.parent as View).updateLayoutParams { width = 600 }
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(240f, endPane.width.toFloat(), absoluteTolerance = 1f)
    }

    private fun createPanes(
        width: Int,
        visible: Boolean = true,
    ) {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        startPane = View(activity)
        endPane = View(activity)
        divider = ResizingDivider(activity)
        val layout =
            LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                isVisible = visible
                addView(startPane, LinearLayout.LayoutParams(0, MATCH_PARENT, 3f))
                addView(divider, LinearLayout.LayoutParams(8, MATCH_PARENT))
                addView(endPane, LinearLayout.LayoutParams(0, MATCH_PARENT, 2f))
            }
        ResizablePaneManager(
            parentLayout = layout,
            divider = divider,
            startPane = startPane,
            endPane = endPane,
            sharedPrefs = prefs,
            startPaneWeightKey = "start",
            endPaneWeightKey = "end",
        )
        activity.setContentView(FrameLayout(activity).apply { addView(layout, FrameLayout.LayoutParams(width, MATCH_PARENT)) })
        shadowOf(Looper.getMainLooper()).idle()
    }

    private fun dragDivider(by: Float) {
        val x = (divider.left + divider.right) / 2f
        val downTime = SystemClock.uptimeMillis()
        divider.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, x, 300f, 0))
        for (step in 1..10) {
            divider.dispatchTouchEvent(
                MotionEvent.obtain(downTime, downTime + step * 10L, MotionEvent.ACTION_MOVE, x + by * step / 10, 300f, 0),
            )
        }
        divider.dispatchTouchEvent(MotionEvent.obtain(downTime, downTime + 200, MotionEvent.ACTION_UP, x + by, 300f, 0))
        shadowOf(Looper.getMainLooper()).idle()
    }
}
