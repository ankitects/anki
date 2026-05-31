// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2009 Andrew <andrewdubya@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Nicolas Raoul <nicolas.raoul@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2009 Edu Zamora <edu.zasu@gmail.com>
// SPDX-FileCopyrightText: Copyright (c) 2021 Nicolai Weitkemper <kontakt@nicolaiweitkemper.de>

package com.ichi2.anki

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.Point
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Region
import android.net.Uri
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.annotation.CheckResult
import androidx.annotation.VisibleForTesting
import androidx.core.content.edit
import androidx.core.graphics.scale
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.annotations.NeedsTest
import com.ichi2.anki.common.preferences.sharedPrefs
import com.ichi2.anki.common.time.Time
import com.ichi2.anki.common.time.getTimestamp
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.dialogs.WhiteBoardWidthDialog
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.ui.windows.reviewer.whiteboard.showColorPickerDialog
import com.ichi2.themes.Themes.currentTheme
import com.ichi2.utils.DisplayUtils.getDisplayDimensions
import timber.log.Timber
import java.io.FileNotFoundException
import kotlin.math.abs
import kotlin.math.max
import com.ichi2.anki.common.android.R as CommonR

/**
 * Whiteboard allowing the user to draw the card's answer on the touchscreen.
 */
@SuppressLint("ViewConstructor")
@NeedsTest("15176 ensure whiteboard drawing works")
class Whiteboard(
    activity: AnkiActivity,
    private val handleMultiTouch: Boolean,
    inverted: Boolean,
) : View(activity, null) {
    private val paint: Paint
    private val undo = UndoList()
    private lateinit var bitmap: Bitmap
    private lateinit var canvas: Canvas
    private val path: Path
    private val bitmapPaint: Paint
    private val ankiActivity: AnkiActivity = activity
    private var x = 0f
    private var y = 0f
    private var secondFingerX0 = 0f
    private var secondFingerY0 = 0f
    private var secondFingerX = 0f
    private var secondFingerY = 0f
    private var secondFingerPointerId = 0
    private var secondFingerWithinTapTolerance = false

    var reviewerEraserModeIsToggledOn = false
    var toggleStylus = false
    var isCurrentlyDrawing = false
        private set

    @get:CheckResult
    @get:VisibleForTesting
    var foregroundColor = 0
    private val colorPalette: LinearLayout
    var onPaintColorChangeListener: OnPaintColorChangeListener? = null
    private val currentStrokeWidth: Int
        get() = ankiActivity.sharedPrefs().getInt("whiteBoardStrokeWidth", 6)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.apply {
            drawColor(0)
            drawBitmap(bitmap, 0f, 0f, bitmapPaint)
            drawPath(path, paint)
        }
    }

    /** Handle motion events to draw using the touch screen or to interact with the flashcard behind
     * the whiteboard by using a second finger.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise
     */
    fun handleTouchEvent(event: MotionEvent): Boolean = handleDrawEvent(event) || handleMultiTouchEvent(event)

    /**
     * Handle motion events to draw using the touch screen. Only simple touch events are processed,
     * a multitouch event aborts to current stroke.
     *
     * @param event The motion event.
     * @return True if the event was handled, false otherwise or when drawing was aborted due to
     * detection of a multitouch event.
     */
    private fun handleDrawEvent(event: MotionEvent): Boolean {
        val x = event.x
        val y = event.y
        if (event.getToolType(event.actionIndex) == MotionEvent.TOOL_TYPE_ERASER || reviewerEraserModeIsToggledOn) {
            eraseTouchedStroke(event)
            return true
        }
        if (event.getToolType(event.actionIndex) != MotionEvent.TOOL_TYPE_STYLUS && toggleStylus) {
            return false
        }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (event.buttonState == MotionEvent.BUTTON_STYLUS_PRIMARY) {
                    eraseTouchedStroke(event)
                } else {
                    drawStart(x, y)
                    invalidate()
                }
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (event.buttonState == MotionEvent.BUTTON_STYLUS_PRIMARY) {
                    eraseTouchedStroke(event)
                    return true
                }
                if (isCurrentlyDrawing) {
                    for (i in 0 until event.historySize) {
                        drawAlong(event.getHistoricalX(i), event.getHistoricalY(i))
                    }
                    drawAlong(x, y)
                    invalidate()
                    return true
                }
                false
            }
            MotionEvent.ACTION_UP -> {
                if (isCurrentlyDrawing) {
                    drawFinish()
                    invalidate()
                    return true
                }
                false
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (isCurrentlyDrawing) {
                    drawAbort()
                }
                false
            }
            211, 213 -> {
                if (event.buttonState == MotionEvent.BUTTON_STYLUS_PRIMARY) {
                    eraseTouchedStroke(event)
                }
                true
            }
            else -> false
        }
    }

    // Parse multitouch input to scroll the card behind the whiteboard or click on elements
    private fun handleMultiTouchEvent(event: MotionEvent): Boolean =
        if (handleMultiTouch && event.pointerCount == 2) {
            when (event.actionMasked) {
                MotionEvent.ACTION_POINTER_DOWN -> {
                    reinitializeSecondFinger(event)
                    true
                }
                MotionEvent.ACTION_MOVE -> trySecondFingerScroll(event)
                MotionEvent.ACTION_POINTER_UP -> trySecondFingerClick(event)
                else -> false
            }
        } else {
            false
        }

    /**
     * Erase touched stroke (= path or point)
     * (by toggling the eraser action button on
     *  or by using the eraser button on the stylus pen
     *  or by using the digital eraser)
     */
    private fun eraseTouchedStroke(event: MotionEvent) {
        if (!undoEmpty()) {
            val didErase = undo.erase(event.x.toInt(), event.y.toInt())
            if (didErase) {
                undo.apply()
                if (undoEmpty()) {
                    ankiActivity.invalidateOptionsMenu()
                }
            }
        }
    }

    /**
     * Clear the whiteboard.
     */
    fun clear() {
        bitmap.eraseColor(0)
        undo.clear()
        invalidate()
        ankiActivity.invalidateOptionsMenu()
    }

    /**
     * Undo the last stroke
     */
    fun undo() {
        undo.pop()
        undo.apply()
        if (undoEmpty()) {
            ankiActivity.invalidateOptionsMenu()
        }
    }

    /** @return Whether there are strokes to undo
     */
    fun undoEmpty(): Boolean = undo.empty()

    private fun createBitmap(
        w: Int,
        h: Int,
    ) {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        this.bitmap = bitmap
        canvas = Canvas(bitmap)
        clear()
    }

    private fun createBitmap() {
        // To fix issue #1336, just make the whiteboard big and square.
        val p = displayDimensions
        val bitmapSize = max(p.x, p.y)
        createBitmap(bitmapSize, bitmapSize)
    }

    /**
     * On rotating the device onSizeChanged() helps to stretch the previously created Bitmap rather
     * than creating a new Bitmap which makes sure bitmap doesn't go out of screen.
     */
    override fun onSizeChanged(
        w: Int,
        h: Int,
        oldw: Int,
        oldh: Int,
    ) {
        super.onSizeChanged(w, h, oldw, oldh)
        // createScaledBitmap requires a width and height > 0; #13972
        if (w <= 0 || h <= 0) {
            Timber.w("Width or height <= 0: w: $w h: $h Bitmap couldn't be created with the new size")
            return
        }
        val scaledBitmap: Bitmap = bitmap.scale(w, h, filter = true)
        bitmap = scaledBitmap
        canvas = Canvas(bitmap)
    }

    private fun drawStart(
        x: Float,
        y: Float,
    ) {
        isCurrentlyDrawing = true
        path.reset()
        path.moveTo(x, y)
        this.x = x
        this.y = y
    }

    private fun drawAlong(
        x: Float,
        y: Float,
    ) {
        val dx = abs(x - this.x)
        val dy = abs(y - this.y)
        if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
            path.quadTo(this.x, this.y, (this.x + x) / 2, (this.y + y) / 2)
            this.x = x
            this.y = y
        }
    }

    private fun drawFinish() {
        isCurrentlyDrawing = false
        val pm = PathMeasure(path, false)
        path.lineTo(x, y)
        val paint = Paint(paint)
        val action = if (pm.length > 0) DrawPath(Path(path), paint) else DrawPoint(x, y, paint)
        action.apply(canvas)
        undo.add(action)
        // kill the path so we don't double draw
        path.reset()
        if (undo.size() == 1) {
            ankiActivity.invalidateOptionsMenu()
        }
    }

    private fun drawAbort() {
        drawFinish()
        undo()
    }

    // call this with an ACTION_POINTER_DOWN event to start a new round of detecting drag or tap with
    // a second finger
    private fun reinitializeSecondFinger(event: MotionEvent) {
        secondFingerWithinTapTolerance = true
        secondFingerPointerId = event.getPointerId(event.actionIndex)
        secondFingerX0 = event.getX(event.findPointerIndex(secondFingerPointerId))
        secondFingerY0 = event.getY(event.findPointerIndex(secondFingerPointerId))
    }

    private fun updateSecondFinger(event: MotionEvent): Boolean {
        val pointerIndex = event.findPointerIndex(secondFingerPointerId)
        if (pointerIndex > -1) {
            secondFingerX = event.getX(pointerIndex)
            secondFingerY = event.getY(pointerIndex)
            val dx = abs(secondFingerX0 - secondFingerX)
            val dy = abs(secondFingerY0 - secondFingerY)
            if (dx >= TOUCH_TOLERANCE || dy >= TOUCH_TOLERANCE) {
                secondFingerWithinTapTolerance = false
            }
            return true
        }
        return false
    }

    // call this with an ACTION_POINTER_UP event to check whether it matches a tap of the second finger
    // if so, forward a click action and return true
    private fun trySecondFingerClick(event: MotionEvent): Boolean {
        if (secondFingerPointerId == event.getPointerId(event.actionIndex)) {
            updateSecondFinger(event)
            if (secondFingerWithinTapTolerance && whiteboardMultiTouchMethods != null) {
                whiteboardMultiTouchMethods!!.tapOnCurrentCard(secondFingerX.toInt(), secondFingerY.toInt())
                return true
            }
        }
        return false
    }

    // call this with an ACTION_MOVE event to check whether it is within the threshold for a tap of the second finger
    // in this case perform a scroll action
    private fun trySecondFingerScroll(event: MotionEvent): Boolean {
        if (updateSecondFinger(event) && !secondFingerWithinTapTolerance) {
            val dy = (secondFingerY0 - secondFingerY).toInt()
            if (dy != 0 && whiteboardMultiTouchMethods != null) {
                whiteboardMultiTouchMethods!!.scrollCurrentCardBy(dy)
                secondFingerX0 = secondFingerX
                secondFingerY0 = secondFingerY
            }
            return true
        }
        return false
    }

    fun onClick(view: View) {
        when (view.id) {
            R.id.pen_color_white -> {
                penColor = Color.WHITE
            }
            R.id.pen_color_black -> {
                penColor = Color.BLACK
            }
            R.id.pen_color_red -> {
                val redPenColor = context.getColor(CommonR.color.material_red_500)
                penColor = redPenColor
            }
            R.id.pen_color_green -> {
                val greenPenColor = context.getColor(CommonR.color.material_green_500)
                penColor = greenPenColor
            }
            R.id.pen_color_blue -> {
                val bluePenColor = context.getColor(CommonR.color.material_blue_500)
                penColor = bluePenColor
            }
            R.id.pen_color_yellow -> {
                val yellowPenColor = context.getColor(CommonR.color.material_yellow_500)
                penColor = yellowPenColor
            }
            R.id.pen_color_custom -> {
                context.showColorPickerDialog(penColor) { penColor = it }
            }
            R.id.stroke_width -> {
                handleWidthChangeDialog()
            }
        }
    }

    private fun handleWidthChangeDialog() {
        val whiteBoardWidthDialog = WhiteBoardWidthDialog(ankiActivity, currentStrokeWidth)
        whiteBoardWidthDialog.onStrokeWidthChanged { wbStrokeWidth: Int -> saveStrokeWidth(wbStrokeWidth) }
        whiteBoardWidthDialog.showStrokeWidthDialog()
    }

    private fun saveStrokeWidth(wbStrokeWidth: Int) {
        paint.strokeWidth = wbStrokeWidth.toFloat()
        ankiActivity.sharedPrefs().edit {
            putInt("whiteBoardStrokeWidth", wbStrokeWidth)
        }
    }

    @get:VisibleForTesting
    var penColor: Int
        get() = paint.color
        set(color) {
            Timber.d("Setting pen color to %d", color)
            paint.color = color
            colorPalette.visibility = GONE
            onPaintColorChangeListener?.onPaintColorChange(color)
        }

    /**
     * Keep a list of all points and paths so that the last stroke can be undone
     * pop() removes the last stroke from the list, and apply() redraws it to whiteboard.
     */
    private inner class UndoList {
        private val list: MutableList<WhiteboardAction> = ArrayList()

        fun add(action: WhiteboardAction) {
            list.add(action)
        }

        fun clear() {
            list.clear()
        }

        fun size(): Int = list.size

        fun pop() {
            list.removeAt(list.size - 1)
        }

        fun apply() {
            bitmap.eraseColor(0)
            for (action in list) {
                action.apply(canvas)
            }
            invalidate()
        }

        fun erase(
            x: Int,
            y: Int,
        ): Boolean {
            var didErase = false
            val clip = Region(0, 0, displayDimensions.x, displayDimensions.y)
            val eraserPath = Path()
            eraserPath.addRect((x - 10).toFloat(), (y - 10).toFloat(), (x + 10).toFloat(), (y + 10).toFloat(), Path.Direction.CW)
            val eraserRegion = Region()
            eraserRegion.setPath(eraserPath, clip)

            // used inside the loop – created here to make things a little more efficient
            val bounds = RectF()
            var lineRegion = Region()

            // we delete elements while iterating, so we need to use an iterator in order to avoid java.util.ConcurrentModificationException
            val iterator = list.iterator()
            while (iterator.hasNext()) {
                val action = iterator.next()
                val path = action.path
                if (path != null) { // → line
                    val lineRegionSuccess = lineRegion.setPath(path, clip)
                    if (!lineRegionSuccess) {
                        // Small lines can be perfectly vertical/horizontal,
                        // thus giving us an empty region, which would make them undeletable.
                        // For this edge case, we create a Region ourselves.
                        path.computeBounds(bounds, true)
                        lineRegion =
                            Region(
                                Rect(
                                    bounds.left.toInt(),
                                    bounds.top.toInt(),
                                    bounds.right.toInt() + 1,
                                    bounds.bottom.toInt() + 1,
                                ),
                            )
                    }
                } else { // → point
                    val p = action.point
                    lineRegion = Region(p!!.x, p.y, p.x + 1, p.y + 1)
                }
                if (!lineRegion.quickReject(eraserRegion) && lineRegion.op(eraserRegion, Region.Op.INTERSECT)) {
                    iterator.remove()
                    didErase = true
                }
            }
            return didErase
        }

        fun empty(): Boolean = list.isEmpty()
    }

    private interface WhiteboardAction {
        fun apply(canvas: Canvas)

        val path: Path?
        val point: Point?
    }

    private class DrawPoint(
        private val x: Float,
        private val y: Float,
        private val paint: Paint,
    ) : WhiteboardAction {
        override fun apply(canvas: Canvas) {
            canvas.drawPoint(x, y, paint)
        }

        override val path: Path?
            get() = null

        override val point: Point
            get() = Point(x.toInt(), y.toInt())
    }

    private class DrawPath(
        override val path: Path,
        private val paint: Paint,
    ) : WhiteboardAction {
        override fun apply(canvas: Canvas) {
            canvas.drawPath(path, paint)
        }

        override val point: Point?
            get() = null
    }

    @Throws(FileNotFoundException::class)
    fun saveWhiteboard(time: Time?): Uri {
        val bitmap = Bitmap.createBitmap(this.width, this.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (foregroundColor != Color.BLACK) {
            canvas.drawColor(Color.BLACK)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        draw(canvas)
        val baseFileName = "Whiteboard" + getTimestamp(time!!)
        return CompatHelper.compat.saveImage(context, bitmap, baseFileName, "jpg", Bitmap.CompressFormat.JPEG, 95)
    }

    fun interface OnPaintColorChangeListener {
        fun onPaintColorChange(color: Int?)
    }

    companion object {
        private const val TOUCH_TOLERANCE = 4f
        private var whiteboardMultiTouchMethods: WhiteboardMultiTouchMethods? = null

        fun createInstance(
            context: AnkiActivity,
            handleMultiTouch: Boolean,
            whiteboardMultiTouchMethods: WhiteboardMultiTouchMethods?,
        ): Whiteboard {
            val whiteboard = Whiteboard(context, handleMultiTouch, currentTheme is NightTheme)
            Companion.whiteboardMultiTouchMethods = whiteboardMultiTouchMethods
            val lp2 =
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            whiteboard.layoutParams = lp2
            val fl = context.findViewById<FrameLayout>(R.id.whiteboard)
            fl.addView(whiteboard)
            whiteboard.isEnabled = true
            return whiteboard
        }

        private val displayDimensions: Point
            get() = getDisplayDimensions(appContext)
    }

    init {
        val whitePenColorButton = activity.findViewById<Button>(R.id.pen_color_white)
        val blackPenColorButton = activity.findViewById<Button>(R.id.pen_color_black)
        if (!inverted) {
            whitePenColorButton.visibility = GONE
            blackPenColorButton.setOnClickListener { view: View -> onClick(view) }
            foregroundColor = Color.BLACK
        } else {
            blackPenColorButton.visibility = GONE
            whitePenColorButton.setOnClickListener { view: View -> onClick(view) }
            foregroundColor = Color.WHITE
        }
        paint =
            Paint().apply {
                isAntiAlias = true
                isDither = true
                color = foregroundColor
                style = Paint.Style.STROKE
                strokeJoin = Paint.Join.ROUND
                strokeCap = Paint.Cap.ROUND
                strokeWidth = currentStrokeWidth.toFloat()
            }
        createBitmap()
        path = Path()
        bitmapPaint = Paint(Paint.DITHER_FLAG)

        // selecting pen color to draw
        colorPalette = activity.findViewById(R.id.whiteboard_editor)
        activity.findViewById<View>(R.id.pen_color_red).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_green).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_blue).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_yellow).setOnClickListener { view: View -> onClick(view) }
        activity.findViewById<View>(R.id.pen_color_custom).apply {
            setOnClickListener { view: View -> onClick(view) }
        }
        activity.findViewById<View>(R.id.stroke_width).apply {
            setOnClickListener { view: View -> onClick(view) }
        }
    }
}
