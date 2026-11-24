// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import androidx.core.graphics.createBitmap
import com.ichi2.anki.R
import com.ichi2.anki.ui.windows.reviewer.whiteboard.SmoothPath.Companion.drawPath
import timber.log.Timber

/**
 * A custom view for the whiteboard that handles drawing and touch events.
 */
class WhiteboardView : View {
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context) : this(context, null)

    var onNewPath: ((Path) -> Unit)? = null
    var onEraseGestureStart: ((Float, Float) -> Unit)? = null
    var onEraseGestureMove: ((Float, Float) -> Unit)? = null
    var onEraseGestureEnd: (() -> Unit)? = null
    var onEraseGestureCancel: (() -> Unit)? = null
    var onStylusButtonStateChanged: ((Boolean) -> Unit)? = null

    var activeTool: WhiteboardTool = WhiteboardTool.Brush(Color.BLACK, WhiteboardRepository.DEFAULT_STROKE_WIDTH)
        set(value) {
            field = value
            if (value is WhiteboardTool.Brush) {
                isStylusButtonOverriding = false
            }
            when (value) {
                is WhiteboardTool.Brush -> {
                    currentPaint.color = value.color
                    currentPaint.strokeWidth = value.width
                }
                is WhiteboardTool.Eraser -> {
                    eraserPreviewPaint.strokeWidth = value.width
                }
            }
            invalidate()
        }

    var isStylusOnlyMode: Boolean = false

    /**
     * Whether the stylus button is currently overriding the active [WhiteboardTool.Brush]
     * with the eraser tool.
     */
    var isStylusButtonOverriding = false
        internal set

    private val currentPath = SmoothPath()
    private val currentPaint =
        Paint().apply {
            isAntiAlias = true
            isDither = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
    private val eraserPreviewPaint =
        Paint(currentPaint).apply {
            color = context.getColor(R.color.whiteboard_eraser)
        }
    private var history: List<DrawingAction> = emptyList()
    private lateinit var bufferCanvas: Canvas
    private lateinit var bufferBitmap: Bitmap
    private val canvasPaint = Paint(Paint.DITHER_FLAG)

    private var hasMoved = false
    private var isDrawing = false
    private val multiTouchDetector =
        MultiTouchDetector(
            touchSlop = ViewConfiguration.get(context).scaledTouchSlop,
        )

    fun setOnMultiTouchListener(listener: OnMultiTouchListener) {
        multiTouchDetector.setOnMultiTouchListener(listener)
    }

    fun setOnScrollByListener(listener: OnScrollByListener) {
        multiTouchDetector.setOnScrollByListener(listener)
    }

    /**
     * Recreates the drawing buffer when the view size changes.
     */
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        // createBitmap requires a width and height > 0; #21096
        if (w <= 0 || h <= 0) {
            Timber.w("Width or height <= 0: w: $w h: $h Bitmap couldn't be created with the new size")
            return
        }
        if (::bufferBitmap.isInitialized) bufferBitmap.recycle()
        bufferBitmap = createBitmap(w, h)
        bufferCanvas = Canvas(bufferBitmap)
        redrawHistory()
    }

    /**
     * Draws the whiteboard content.
     * This includes the historical drawing buffer and the current live path.
     */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Draw the committed history
        canvas.drawBitmap(bufferBitmap, 0f, 0f, canvasPaint)

        if (activeTool is WhiteboardTool.Eraser) {
            canvas.drawPath(currentPath, eraserPreviewPaint)
        } else {
            canvas.drawPath(currentPath, currentPaint)
        }
    }

    /**
     * Handles user touch input for drawing and erasing.
     * In stylus-only mode, finger input passes through to the card beneath the whiteboard.
     */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.pointerCount >= 2) {
            isDrawing = false
            currentPath.reset()
            invalidate()
            onEraseGestureCancel?.invoke()

            return multiTouchDetector.onTouchEvent(event)
        }

        if (isStylusOnlyMode && !event.isStylus) {
            return false
        }

        val touchX = event.x
        val touchY = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                updateStylusButtonState(event)
                isDrawing = true
                hasMoved = false
                currentPath.moveTo(touchX, touchY)
                // TODO: #22553 - don't erase until the gesture becomes an erase, to avoid the flicker
                if (isStrokeEraser) {
                    onEraseGestureStart?.invoke(touchX, touchY)
                }
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDrawing) return false

                val isButtonPressed = event.hasStylusButtonPressed()
                if (isButtonPressed != isStylusButtonOverriding && isToolChange(isButtonPressed)) {
                    hasMoved = true
                    currentPath.drawAlong(event)
                    if (isStrokeEraser) {
                        onEraseGestureMove?.invoke(touchX, touchY)
                    }
                    handleMidStrokeToolChange(touchX, touchY, isButtonPressed)
                } else {
                    hasMoved = true
                    currentPath.drawAlong(event)
                    if (isStrokeEraser) {
                        onEraseGestureMove?.invoke(touchX, touchY)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_BUTTON_PRESS,
            MotionEvent.ACTION_BUTTON_RELEASE,
            -> {
                if (!isDrawing) {
                    updateStylusButtonState(event)
                    return true
                }
                val isButtonPressed = event.hasStylusButtonPressed()
                if (isButtonPressed != isStylusButtonOverriding && isToolChange(isButtonPressed)) {
                    handleMidStrokeToolChange(touchX, touchY, isButtonPressed)
                }
            }
            MotionEvent.ACTION_UP -> {
                if (!isDrawing) return false

                commitCurrentStroke(touchX, touchY)
                currentPath.reset()
                isDrawing = false
                updateStylusButtonState(event)
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> {
                currentPath.reset()
                onEraseGestureCancel?.invoke()
                isDrawing = false
                updateStylusButtonState(event)
                invalidate()
            }
            else -> return false
        }
        return true
    }

    override fun onHoverEvent(event: MotionEvent): Boolean {
        updateStylusButtonState(event)
        return super.onHoverEvent(event)
    }

    private val isStrokeEraser: Boolean
        get() = activeTool is WhiteboardTool.Eraser && (activeTool as WhiteboardTool.Eraser).mode == EraserMode.STROKE

    /**
     * Determines whether [isButtonPressed] represents a tool change.
     *
     * A button press is a tool change only if the active tool is a [WhiteboardTool.Brush].
     * A button release is a tool change only if the button was actively overriding a brush.
     */
    private fun isToolChange(isButtonPressed: Boolean): Boolean =
        if (isButtonPressed) {
            activeTool is WhiteboardTool.Brush
        } else {
            isStylusButtonOverriding
        }

    private fun commitCurrentStroke(
        touchX: Float,
        touchY: Float,
    ) {
        if (isStrokeEraser) {
            onEraseGestureEnd?.invoke()
        } else {
            if (!hasMoved) {
                // A single tap. Add a tiny line segment to ensure it has a non-zero length,
                // which makes it more robust for path operations.
                currentPath.lineTo(touchX + 0.2f, touchY + 0.2f)
            }
            onNewPath?.invoke(currentPath.clone())
        }
    }

    private fun handleMidStrokeToolChange(
        touchX: Float,
        touchY: Float,
        isButtonPressed: Boolean,
    ) {
        commitCurrentStroke(touchX, touchY)
        currentPath.reset()
        hasMoved = false
        currentPath.moveTo(touchX, touchY)

        isStylusButtonOverriding = isButtonPressed
        onStylusButtonStateChanged?.invoke(isButtonPressed)

        if (isStrokeEraser) {
            onEraseGestureStart?.invoke(touchX, touchY)
        }
        invalidate()
    }

    private val MotionEvent.isStylus: Boolean
        get() =
            (0 until pointerCount).any {
                val toolType = getToolType(it)
                toolType == MotionEvent.TOOL_TYPE_STYLUS || toolType == MotionEvent.TOOL_TYPE_ERASER
            }

    private fun MotionEvent.hasStylusButtonPressed(): Boolean {
        if (!isStylus) return false
        val stylusButtons =
            MotionEvent.BUTTON_STYLUS_PRIMARY or
                MotionEvent.BUTTON_STYLUS_SECONDARY or
                MotionEvent.BUTTON_SECONDARY
        return (buttonState and stylusButtons) != 0
    }

    private fun updateStylusButtonState(event: MotionEvent) {
        val isButtonPressed = event.hasStylusButtonPressed()
        if (isButtonPressed != isStylusButtonOverriding && isToolChange(isButtonPressed)) {
            isStylusButtonOverriding = isButtonPressed
            onStylusButtonStateChanged?.invoke(isButtonPressed)
        }
    }

    /**
     * Replaces the current drawing history with a new set of actions and redraws the buffer.
     */
    fun setHistory(actions: List<DrawingAction>) {
        history = actions
        redrawHistory()
    }

    /**
     * Redraws all historical paths onto the offscreen buffer.
     */
    private fun redrawHistory() {
        if (!::bufferCanvas.isInitialized) return
        bufferCanvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        val tempPaint =
            Paint().apply {
                isAntiAlias = true
                isDither = true
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
            }
        for (action in history) {
            tempPaint.strokeWidth = action.strokeWidth
            if (action.isEraser) {
                tempPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            } else {
                tempPaint.xfermode = null
                tempPaint.color = action.color
            }
            bufferCanvas.drawPath(action.path, tempPaint)
        }
        invalidate()
    }
}

/**
 * A wrapper around a [Path] which supports smooth drawing & state tracking via [drawAlong]
 */
private class SmoothPath(
    private val path: Path = Path(),
) {
    // for efficiency use two primitives rather than a 'point' class
    private var lastX = 0f
    private var lastY = 0f

    /**
     * Extracts and draws a smooth curve from the [MotionEvent]
     */
    fun drawAlong(event: MotionEvent) {
        // use historySize for cases when the touchscreen samples faster than the screen
        for (i in 0 until event.historySize) {
            val hx = event.getHistoricalX(i)
            val hy = event.getHistoricalY(i)
            // draw Bézier curves between the midpoints, ensuring a continuous curve
            path.quadTo(lastX, lastY, (lastX + hx) / 2f, (lastY + hy) / 2f)
            lastX = hx
            lastY = hy
        }
        // draw the current event
        val x = event.x
        val y = event.y
        path.quadTo(lastX, lastY, (lastX + x) / 2f, (lastY + y) / 2f)
        lastX = x
        lastY = y
    }

    // Methods are reimplemented rather than using inheritance to ensure nothing is forgotten

    /** @see Path.lineTo */
    fun lineTo(
        x: Float,
        y: Float,
    ) {
        path.lineTo(x, y)
        lastX = x
        lastY = y
    }

    /** @see Path.moveTo */
    fun moveTo(
        x: Float,
        y: Float,
    ) {
        path.moveTo(x, y)
        lastX = x
        lastY = y
    }

    /** @see Path.reset */
    fun reset() {
        path.reset()
        lastX = 0f
        lastY = 0f
    }

    fun clone() = Path(path)

    companion object {
        /** @see Canvas.drawPath */
        fun Canvas.drawPath(
            path: SmoothPath,
            paint: Paint,
        ) {
            this.drawPath(path.path, paint)
        }
    }
}
