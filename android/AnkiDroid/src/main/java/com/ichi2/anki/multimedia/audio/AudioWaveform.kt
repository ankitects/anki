/*
 *  Copyright (c) 2023 Ashish Yadav <mailtoashish693@gmail.com>
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
package com.ichi2.anki.multimedia.audio

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import androidx.core.content.withStyledAttributes
import com.ichi2.anki.R
import kotlin.math.ceil

/**This class represents a custom View used for creating audio waveforms when recording audio.
 * It loops over each spike and add it on the screen and the height of the spike is determined by the
 * amplitude that is returned by the audio recorder while recording audio. **/
class AudioWaveform(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private var spikePaint =
        Paint().apply {
            color = Color.rgb(244, 81, 30)
        }

    private var verticalLinePaint =
        Paint().apply {
            color = Color.rgb(33, 150, 243)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }

    private var backgroundPaint =
        Paint().apply {
            color = Color.argb(20, 229, 228, 226)
        }

    private var amplitudes = ArrayList<Float>()
    private var audioSpikes = ArrayList<RectF>()

    private var radius = 3f

    /** Width of the audio spike **/
    private var w = 6f

    /** Screen width, it's updated according to the actual screen width **/
    private val sw get() = width

    /** Gap between each spike **/
    private var d = 4f

    // Intended to be `val` but declared a `var` instead to allow initialization from the init {} block
    private var displayVerticalLine: Boolean = true

    /**
     * If the vertical line is displayed, the waveform is drawn up to the line
     * Otherwise, the waveform takes up the full width of the control
     */
    private val percentageOfWidthToFill: Float
        get() = if (displayVerticalLine) 0.5f else 1f

    private val spikeCount
        get() =
            ceil(sw / (w + d) * percentageOfWidthToFill)
                .toInt()

    init {
        context.withStyledAttributes(attrs, R.styleable.AudioWaveform, 0, 0) {
            displayVerticalLine = getBoolean(R.styleable.AudioWaveform_display_vertical_line, true)
            backgroundPaint.color = getColor(R.styleable.AudioWaveform_android_background, backgroundPaint.color)
        }
    }

    fun addAmplitude(amp: Float) {
        // minimum height 6 is assigned by default to avoid blank spikes, making the UI consistent
        val norm = (amp.toInt() / 7).coerceAtMost(300).coerceAtLeast(6).toFloat()
        amplitudes.add(norm)
        audioSpikes.clear()
        val amps = amplitudes.takeLast(spikeCount)
        for ((index, amplitude) in amps.withIndex()) {
            val left = index * (w + d)
            val top = height / 2 - amplitude / 2
            val right = left + w
            val bottom = top + amplitude
            audioSpikes.add(RectF(left, top, right, bottom))
        }
        invalidate()
    }

    fun clear(): ArrayList<*> {
        val amps = amplitudes.clone() as ArrayList<*>
        amplitudes.clear()
        audioSpikes.clear()
        invalidate()
        return amps
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        audioSpikes.forEach {
            canvas.drawRoundRect(it, radius, radius, spikePaint)
        }

        if (displayVerticalLine) {
            val maxCenterX = width / 2f
            val isFilled = audioSpikes.size == spikeCount

            val centerX =
                if (isFilled) {
                    maxCenterX
                } else {
                    val currentLineX = audioSpikes.size * (w + d)

                    currentLineX.coerceAtMost(maxCenterX)
                }

            val startY = 0f
            val endY = height.toFloat()
            canvas.drawLine(centerX, startY, centerX, endY, verticalLinePaint)
        }
    }
}
