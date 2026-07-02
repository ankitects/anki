/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
 */

package com.ichi2.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors
import com.ichi2.utils.dp
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

typealias MeasureSpecValue = Int

/**
 * Displays the [value] of a given axis on a [-1, 1] scale using a colored bar
 *
 * If the value is [an extremity][isExtremity], the color of the bar is changed
 *
 * Invokes [extremityListener] each time an 'extreme' value is provided
 */
class AxisValueDisplay(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    /** Called when [isExtremity] returns true */
    private var extremityListener: ((value: Float) -> Unit)? = null

    /** The default color of the value of an axis when not at an extreme */
    private val normalPaint =
        Paint().apply {
            color = Color.BLUE
        }

    /** The color when an axis is at {-1, 1} and is selectable */
    private val extremityPaint =
        Paint().apply {
            color = Color.GREEN
        }

    /** The current value */
    var value = 0f
        set(value) {
            field = value
            this.postInvalidate()
            text = String.format(Locale.getDefault(), "%.3f", value)
            if (isExtremity) {
                extremityListener?.invoke(field)
            }
        }

    /** Pre-calculated value to speed up calls to [onDraw] */
    private var text: String = ""

    /** @return [value] is -1 or 1 */
    private val isExtremity get() = abs(value) == 1f

    /** The current color of the bar */
    private val barPaint get() = if (isExtremity) extremityPaint else normalPaint

    /** [Paint] for the text displaying the [value] */
    private val textPaint =
        Paint().apply {
            textAlign = Paint.Align.CENTER
            textSize = 20.dp.toPx(context).toFloat()
            color = MaterialColors.getColor(context, android.R.attr.textColor, 0)
        }

    /** stores text dimensions, used for centering text */
    private val textBounds = Rect()

    private val controlHeight = 40.dp.toPx(context)

    override fun onMeasure(
        widthMeasureSpec: Int,
        heightMeasureSpec: Int,
    ) {
        val width = widthMeasureSpec.constrainTo(suggestedMinimumWidth)
        val height = heightMeasureSpec.constrainTo(controlHeight)
        setMeasuredDimension(width, height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // draws a bar so a value of 0 is in the middle, -1 is on the left, and +1 is on the right
        val halfway = width / 2f
        val left = min(halfway + halfway * value, halfway)
        val right = max(halfway, halfway + halfway * value)

        canvas.drawRect(left, 0f, right, height.toFloat(), barPaint)

        // https://stackoverflow.com/a/24969713/
        // draw text in the center horizontally + vertically, handling font & bounding box
        textBounds.updateAsTextBounds(textPaint, text)
        canvas.drawText(text, halfway, height.toFloat() / 2 - textBounds.exactCenterY(), textPaint)
    }

    fun setExtremityListener(listener: (value: Float) -> Unit) {
        this.extremityListener = listener
    }
}

/** Handles [View.MeasureSpec] constraints to provide a height/width given a desired value */
private fun MeasureSpecValue.constrainTo(desiredValue: Int): Int {
    // https://stackoverflow.com/a/12267248
    val mode = View.MeasureSpec.getMode(this)
    val size = View.MeasureSpec.getSize(this)

    return when (mode) {
        View.MeasureSpec.EXACTLY -> {
            size
        }
        View.MeasureSpec.AT_MOST -> {
            min(desiredValue, size)
        }
        else -> {
            desiredValue
        }
    }
}

/**
 * Updates the receiver with the smallest boundary which would accept [text] & [paint]
 * @see [Paint.getTextBounds]
 */
private fun Rect.updateAsTextBounds(
    paint: Paint,
    text: String,
) = paint.getTextBounds(text, 0, text.length, this)
