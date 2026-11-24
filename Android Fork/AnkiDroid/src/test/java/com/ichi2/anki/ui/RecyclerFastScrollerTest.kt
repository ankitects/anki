// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.ui

import android.app.Activity
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.RelativeLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.testutils.EmptyApplication
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class RecyclerFastScrollerTest {
    @Test
    fun `scroller hides after a thumb drag`() {
        val list = FastScrollerTestList(evenRows(200))
        list.press()
        list.moveTo(trackProportion = 0.5f)
        list.release()

        list.waitPastAutoHide()

        assertThat(list.isThumbPressed, equalTo(false))
        assertThat(list.isScrollerHidden, equalTo(true))
    }

    @Test
    fun `scroller hides after the thumb is held still past the hide delay`() {
        val list = FastScrollerTestList(evenRows(200))
        list.press()
        list.moveTo(trackProportion = 0.5f)
        list.waitPastAutoHide()
        list.release()

        list.waitPastAutoHide()

        assertThat(list.isScrollerHidden, equalTo(true))
    }
}

private const val ROW_HEIGHT = 150

private fun evenRows(count: Int) = List(count) { ROW_HEIGHT }

private class FastScrollerTestList(
    rowHeights: List<Int>,
) {
    private val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    private val recyclerView =
        RecyclerView(activity).also {
            it.layoutManager = LinearLayoutManager(activity)
            it.adapter = FixedHeightRowsAdapter(rowHeights)
        }
    private val scroller = RecyclerFastScroller(activity)
    private val root =
        RelativeLayout(activity).apply {
            addView(recyclerView, RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
            addView(
                scroller,
                RelativeLayout.LayoutParams(WRAP_CONTENT, MATCH_PARENT).apply { addRule(RelativeLayout.ALIGN_PARENT_END) },
            )
        }
    private val downTime = SystemClock.uptimeMillis()
    private var fingerY = 0f

    val isThumbPressed: Boolean get() = scroller.handle.isPressed

    val isScrollerHidden: Boolean get() = scroller.translationX == scroller.hiddenTranslationX.toFloat()

    init {
        scroller.attachRecyclerView(recyclerView)
        activity.setContentView(root, ViewGroup.LayoutParams(WIDTH, HEIGHT))
        showNextFrame()
    }

    fun press() = touch(MotionEvent.ACTION_DOWN, (scroller.handle.top + scroller.handle.bottom) / 2f)

    fun moveTo(trackProportion: Float) {
        val thumbHeight = scroller.handle.height
        touch(MotionEvent.ACTION_MOVE, thumbHeight / 2f + trackProportion * (HEIGHT - thumbHeight))
    }

    fun release() = touch(MotionEvent.ACTION_UP, fingerY)

    fun waitPastAutoHide() = shadowOf(Looper.getMainLooper()).idleFor(AUTO_HIDE_WAIT)

    private fun touch(
        action: Int,
        y: Float,
    ) {
        fingerY = y
        val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, scroller.width / 2f, y, 0)
        scroller.dispatchTouchEvent(event)
        event.recycle()
        showNextFrame()
    }

    private fun showNextFrame() = shadowOf(Looper.getMainLooper()).idleFor(FRAME_INTERVAL)

    private companion object {
        const val WIDTH = 1000
        const val HEIGHT = 2000
        val FRAME_INTERVAL: Duration = Duration.ofMillis(16)
        val AUTO_HIDE_WAIT: Duration = Duration.ofSeconds(2)
    }
}

private class FixedHeightRowsAdapter(
    private val rowHeights: List<Int>,
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
