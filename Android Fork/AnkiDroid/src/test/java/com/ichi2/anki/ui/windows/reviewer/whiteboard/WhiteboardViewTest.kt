// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.graphics.Color
import android.graphics.Path
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class WhiteboardViewTest {
    private lateinit var whiteboardView: WhiteboardView

    @Before
    fun setUp() {
        whiteboardView = WhiteboardView(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun `stylus mode preserves finger input to the underlying card`() {
        whiteboardView.isStylusOnlyMode = true
        val received = mutableListOf<Int>()
        val container = FrameLayout(whiteboardView.context)
        val card = View(whiteboardView.context)
        card.setOnTouchListener { _, event ->
            received.add(event.actionMasked)
            true
        }
        container.addView(card, FrameLayout.LayoutParams(300, 300))
        container.addView(whiteboardView, FrameLayout.LayoutParams(300, 300))
        val size = View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY)
        container.measure(size, size)
        container.layout(0, 0, 300, 300)

        val actions = listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP)
        actions.forEachIndexed { index, action ->
            val event = MotionEvent.obtain(0L, index * 20L, action, 50f, 50f + index * 40f, 0)
            try {
                container.dispatchTouchEvent(event)
            } finally {
                event.recycle()
            }
        }

        assertEquals(actions, received, "Stylus mode must preserve finger input used for card scrolling, links and gestures")
    }

    @Test
    fun `onTouchEvent notifies onStylusButtonStateChanged when stylus button is pressed and released`() {
        val states = mutableListOf<Boolean>()
        whiteboardView.onStylusButtonStateChanged = { states.add(it) }

        // Touch with stylus button pressed
        val downEvent = createStylusMotionEvent(MotionEvent.ACTION_DOWN, MotionEvent.BUTTON_STYLUS_PRIMARY)
        whiteboardView.onTouchEvent(downEvent)
        downEvent.recycle()

        assertEquals(listOf(true), states)

        // Move with stylus button still pressed
        val moveEvent = createStylusMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY)
        whiteboardView.onTouchEvent(moveEvent)
        moveEvent.recycle()

        // State shouldn't be re-emitted if unchanged
        assertEquals(listOf(true), states)

        // Move with stylus button released
        val moveReleasedEvent = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0)
        whiteboardView.onTouchEvent(moveReleasedEvent)
        moveReleasedEvent.recycle()

        assertEquals(listOf(true, false), states)
    }

    @Test
    fun `onHoverEvent notifies onStylusButtonStateChanged during hover`() {
        val states = mutableListOf<Boolean>()
        whiteboardView.onStylusButtonStateChanged = { states.add(it) }

        val hoverEvent = createStylusMotionEvent(MotionEvent.ACTION_HOVER_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY)
        whiteboardView.onHoverEvent(hoverEvent)
        hoverEvent.recycle()

        assertEquals(listOf(true), states)

        val hoverReleasedEvent = createStylusMotionEvent(MotionEvent.ACTION_HOVER_MOVE, 0)
        whiteboardView.onHoverEvent(hoverReleasedEvent)
        hoverReleasedEvent.recycle()

        assertEquals(listOf(true, false), states)
    }

    @Test
    fun `onHoverEvent without button pressed does not notify onStylusButtonStateChanged`() {
        val states = mutableListOf<Boolean>()
        whiteboardView.onStylusButtonStateChanged = { states.add(it) }

        val hoverEvent = createStylusMotionEvent(MotionEvent.ACTION_HOVER_MOVE, 0)
        whiteboardView.onHoverEvent(hoverEvent)
        hoverEvent.recycle()

        assertTrue(states.isEmpty())
    }

    @Test
    fun `mid-stroke tool change commits in-progress stroke and continues drawing`() {
        val completedPaths = mutableListOf<Path>()
        val toolsAtCommit = mutableListOf<WhiteboardTool>()
        whiteboardView.onNewPath = {
            toolsAtCommit.add(whiteboardView.activeTool)
            completedPaths.add(it)
        }
        whiteboardView.onStylusButtonStateChanged = { isPressed ->
            whiteboardView.activeTool =
                if (isPressed) {
                    WhiteboardTool.Eraser(inkWidth = 20f)
                } else {
                    WhiteboardTool.Brush(Color.BLACK, 10f)
                }
        }

        // 1. Start drawing with Brush (button not pressed)
        val downEvent = createStylusMotionEvent(MotionEvent.ACTION_DOWN, 0, x = 10f, y = 10f)
        whiteboardView.onTouchEvent(downEvent)
        downEvent.recycle()

        val move1 = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 20f, y = 20f)
        whiteboardView.onTouchEvent(move1)
        move1.recycle()

        // 2. Button is pressed mid-stroke: brush stroke commits, drawing continues with ink eraser
        val moveWithButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, x = 30f, y = 30f)
        whiteboardView.onTouchEvent(moveWithButton)
        moveWithButton.recycle()

        assertEquals(1, completedPaths.size)
        assertTrue(whiteboardView.isEraserActive)

        // Continue moving with eraser
        val move2 = createStylusMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, x = 40f, y = 40f)
        whiteboardView.onTouchEvent(move2)
        move2.recycle()

        // 3. Button is released mid-stroke: eraser stroke commits, drawing continues with brush
        val moveWithoutButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 50f, y = 50f)
        whiteboardView.onTouchEvent(moveWithoutButton)
        moveWithoutButton.recycle()

        assertEquals(2, completedPaths.size)
        assertFalse(whiteboardView.isEraserActive)

        // 4. Finish stroke on UP: last brush stroke commits
        val upEvent = createStylusMotionEvent(MotionEvent.ACTION_UP, 0, x = 60f, y = 60f)
        whiteboardView.onTouchEvent(upEvent)
        upEvent.recycle()

        assertEquals(3, completedPaths.size)
        assertEquals(
            listOf(
                WhiteboardTool.Brush(Color.BLACK, 10f),
                WhiteboardTool.Eraser(inkWidth = 20f),
                WhiteboardTool.Brush(Color.BLACK, 10f),
            ),
            toolsAtCommit,
        )
    }

    @Test
    fun `mid-stroke tool change with stroke eraser starts and ends erase gesture`() {
        val completedPaths = mutableListOf<Path>()
        val eraseGestureEvents = mutableListOf<String>()
        whiteboardView.onNewPath = { completedPaths.add(it) }
        whiteboardView.onEraseGestureStart = { x, y -> eraseGestureEvents.add("start $x,$y") }
        whiteboardView.onEraseGestureMove = { x, y -> eraseGestureEvents.add("move $x,$y") }
        whiteboardView.onEraseGestureEnd = { eraseGestureEvents.add("end") }

        whiteboardView.onStylusButtonStateChanged = { isPressed ->
            whiteboardView.activeTool =
                if (isPressed) {
                    WhiteboardTool.Eraser(mode = EraserMode.STROKE, strokeEraserWidth = 20f)
                } else {
                    WhiteboardTool.Brush(Color.BLACK, 10f)
                }
        }

        // 1. Draw brush
        val downEvent = createStylusMotionEvent(MotionEvent.ACTION_DOWN, 0, x = 10f, y = 10f)
        whiteboardView.onTouchEvent(downEvent)
        downEvent.recycle()

        val move1 = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 20f, y = 20f)
        whiteboardView.onTouchEvent(move1)
        move1.recycle()

        // 2. Press button: brush stroke commits, stroke erase gesture starts
        val moveWithButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, x = 30f, y = 30f)
        whiteboardView.onTouchEvent(moveWithButton)
        moveWithButton.recycle()

        assertEquals(1, completedPaths.size)
        assertEquals(listOf("start 30.0,30.0"), eraseGestureEvents)

        // 3. Move with eraser
        val move2 = createStylusMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, x = 40f, y = 40f)
        whiteboardView.onTouchEvent(move2)
        move2.recycle()

        assertEquals(listOf("start 30.0,30.0", "move 40.0,40.0"), eraseGestureEvents)

        // 4. Release button: stroke erase gesture ends, brush stroke starts
        val moveWithoutButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 50f, y = 50f)
        whiteboardView.onTouchEvent(moveWithoutButton)
        moveWithoutButton.recycle()

        assertEquals(listOf("start 30.0,30.0", "move 40.0,40.0", "move 50.0,50.0", "end"), eraseGestureEvents)

        // 5. Finish on UP: brush stroke commits
        val upEvent = createStylusMotionEvent(MotionEvent.ACTION_UP, 0, x = 60f, y = 60f)
        whiteboardView.onTouchEvent(upEvent)
        upEvent.recycle()

        assertEquals(2, completedPaths.size)
    }

    @Test
    fun `when activeTool is already ink eraser pressing stylus button does not stop stroke`() {
        val completedPaths = mutableListOf<Path>()
        whiteboardView.onNewPath = { completedPaths.add(it) }
        whiteboardView.activeTool = WhiteboardTool.Eraser(inkWidth = 20f)

        val downEvent = createStylusMotionEvent(MotionEvent.ACTION_DOWN, 0, x = 10f, y = 10f)
        whiteboardView.onTouchEvent(downEvent)
        downEvent.recycle()

        val move1 = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 20f, y = 20f)
        whiteboardView.onTouchEvent(move1)
        move1.recycle()

        // Press button while already using eraser: stroke should NOT stop
        val moveWithButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, x = 30f, y = 30f)
        whiteboardView.onTouchEvent(moveWithButton)
        moveWithButton.recycle()

        assertEquals(0, completedPaths.size)

        // Release button while already using eraser: stroke should NOT stop
        val moveWithoutButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 40f, y = 40f)
        whiteboardView.onTouchEvent(moveWithoutButton)
        moveWithoutButton.recycle()

        assertEquals(0, completedPaths.size)

        // Finish on UP: single complete stroke commits
        val upEvent = createStylusMotionEvent(MotionEvent.ACTION_UP, 0, x = 50f, y = 50f)
        whiteboardView.onTouchEvent(upEvent)
        upEvent.recycle()

        assertEquals(1, completedPaths.size)
    }

    @Test
    fun `when activeTool is already stroke eraser pressing stylus button does not stop stroke`() {
        val eraseGestureEvents = mutableListOf<String>()
        whiteboardView.onEraseGestureStart = { x, y -> eraseGestureEvents.add("start $x,$y") }
        whiteboardView.onEraseGestureMove = { x, y -> eraseGestureEvents.add("move $x,$y") }
        whiteboardView.onEraseGestureEnd = { eraseGestureEvents.add("end") }
        whiteboardView.activeTool = WhiteboardTool.Eraser(mode = EraserMode.STROKE, strokeEraserWidth = 20f)

        val downEvent = createStylusMotionEvent(MotionEvent.ACTION_DOWN, 0, x = 10f, y = 10f)
        whiteboardView.onTouchEvent(downEvent)
        downEvent.recycle()

        val move1 = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 20f, y = 20f)
        whiteboardView.onTouchEvent(move1)
        move1.recycle()

        // Press button: should continue same erase gesture
        val moveWithButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, MotionEvent.BUTTON_STYLUS_PRIMARY, x = 30f, y = 30f)
        whiteboardView.onTouchEvent(moveWithButton)
        moveWithButton.recycle()

        // Release button: should continue same erase gesture
        val moveWithoutButton = createStylusMotionEvent(MotionEvent.ACTION_MOVE, 0, x = 40f, y = 40f)
        whiteboardView.onTouchEvent(moveWithoutButton)
        moveWithoutButton.recycle()

        // Finish on UP
        val upEvent = createStylusMotionEvent(MotionEvent.ACTION_UP, 0, x = 50f, y = 50f)
        whiteboardView.onTouchEvent(upEvent)
        upEvent.recycle()

        assertEquals(
            listOf(
                "start 10.0,10.0",
                "move 20.0,20.0",
                "move 30.0,30.0",
                "move 40.0,40.0",
                "end",
            ),
            eraseGestureEvents,
        )
    }

    @Test
    fun `activeTool updates isEraserActive and eraserMode`() {
        whiteboardView.activeTool = WhiteboardTool.Brush(Color.RED, 15f)
        assertFalse(whiteboardView.isEraserActive)

        whiteboardView.activeTool = WhiteboardTool.Eraser(mode = EraserMode.STROKE, strokeEraserWidth = 25f)
        assertTrue(whiteboardView.isEraserActive)
        assertEquals(EraserMode.STROKE, whiteboardView.eraserMode)
    }

    private fun createStylusMotionEvent(
        action: Int,
        buttonState: Int,
        x: Float = 50f,
        y: Float = 50f,
    ): MotionEvent {
        val properties =
            arrayOf(
                MotionEvent.PointerProperties().apply {
                    id = 0
                    toolType = MotionEvent.TOOL_TYPE_STYLUS
                },
            )
        val coords =
            arrayOf(
                MotionEvent.PointerCoords().apply {
                    this.x = x
                    this.y = y
                },
            )
        return MotionEvent.obtain(
            0L,
            0L,
            action,
            1,
            properties,
            coords,
            0,
            buttonState,
            1f,
            1f,
            0,
            0,
            InputDevice.SOURCE_STYLUS,
            0,
        )
    }
}

private val WhiteboardView.isEraserActive: Boolean
    get() = activeTool is WhiteboardTool.Eraser

private val WhiteboardView.eraserMode: EraserMode
    get() = (activeTool as? WhiteboardTool.Eraser)?.mode ?: EraserMode.INK
