// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.createBitmap
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.deckAdapter
import com.ichi2.anki.widgets.DeckHierarchyLinesDecoration
import com.ichi2.anki.withDeckPicker
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DeckHierarchyLinesDecorationTest : RobolectricTest() {
    /** Regression test for the AnkiQuest fork, which uses a [ConcatAdapter] to add a review heatmap above the deck list. */
    @Test
    fun `lines are drawn only for deck rows when the deck adapter is wrapped in a ConcatAdapter`() {
        val math = addDeck("Math")
        addDeck("Math::Algebra")
        addDeck("Math::Geometry")

        withDeckPicker(deckCount = 0) { deckPicker ->
            val list = deckPicker.deckPickerBinding.decks
            list.itemAnimator = null
            val deckAdapter = deckPicker.deckAdapter
            deckPicker.viewModel.toggleDeckExpand(math)
            advanceRobolectricLooperUntil(lazyMessage = { "Math was not expanded" }) {
                deckAdapter.currentList.any { it.depth == 1 } && list.childCount == deckAdapter.itemCount
            }
            val decoration = DeckHierarchyLinesDecoration(deckPicker, deckAdapter)
            val unwrapped = decoration.recordDrawing(list)
            assertTrue(unwrapped.isNotEmpty(), "hierarchy lines are drawn for the plain deck adapter")

            // a section above the decks shifts every deck row's absolute adapter position by one
            val header = FixedHeightAdapter(HEADER_HEIGHT_PX)
            list.adapter = ConcatAdapter(header, deckAdapter)
            advanceRobolectricLooperUntil(lazyMessage = { "the header and deck rows were not laid out" }) {
                list.childCount == deckAdapter.itemCount + 1
            }
            val wrapped = decoration.recordDrawing(list)

            assertEquals(
                unwrapped.map { it.offsetY(HEADER_HEIGHT_PX.toFloat()) },
                wrapped,
                "the deck rows are drawn as before, below the header, and nothing is drawn for the header",
            )
        }
    }

    /** The lines and the bounds of the paths [this] draws over [list]. */
    private fun DeckHierarchyLinesDecoration.recordDrawing(list: RecyclerView): List<Segment> {
        val canvas = RecordingCanvas(createBitmap(list.width, list.height))
        onDrawOver(canvas, list, RecyclerView.State())
        return canvas.segments
    }

    private data class Segment(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
    ) {
        fun offsetY(dy: Float) = copy(top = top + dy, bottom = bottom + dy)
    }

    private class RecordingCanvas(
        bitmap: Bitmap,
    ) : Canvas(bitmap) {
        val segments = mutableListOf<Segment>()

        override fun drawLine(
            startX: Float,
            startY: Float,
            stopX: Float,
            stopY: Float,
            paint: Paint,
        ) {
            segments += Segment(startX, startY, stopX, stopY)
        }

        override fun drawPath(
            path: Path,
            paint: Paint,
        ) {
            val bounds = RectF().also { path.computeBounds(it, true) }
            segments += Segment(bounds.left, bounds.top, bounds.right, bounds.bottom)
        }
    }

    /** A single row which isn't a deck */
    private class FixedHeightAdapter(
        private val heightPx: Int,
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = 1

        override fun onCreateViewHolder(
            parent: ViewGroup,
            viewType: Int,
        ): RecyclerView.ViewHolder =
            object : RecyclerView.ViewHolder(
                View(parent.context).apply {
                    layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx)
                },
            ) {}

        override fun onBindViewHolder(
            holder: RecyclerView.ViewHolder,
            position: Int,
        ) {}
    }

    companion object {
        private const val HEADER_HEIGHT_PX = 100
    }
}
