/*
 * Copyright (c) 2025 Divyansh Kushwaha <thedroiddiv@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.ui.windows.reviewer.whiteboard

import android.content.Context
import android.os.Build
import android.view.Choreographer
import android.widget.FrameLayout
import androidx.annotation.ColorInt
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import com.ichi2.anki.R
import com.ichi2.utils.Dp
import com.ichi2.utils.dp
import com.ichi2.utils.negativeButton
import com.ichi2.utils.show
import com.skydoves.colorpickerview.AlphaTileView
import com.skydoves.colorpickerview.ColorEnvelope
import com.skydoves.colorpickerview.ColorPickerDialog
import com.skydoves.colorpickerview.flag.FlagView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar

/**
 * Shows a customizable color picker dialog with alpha and brightness controls.
 *
 * @receiver The Android context used to create the dialog.
 * @param initialColor The initial color to display in the picker as an `AARRGGBB` integer.
 * @param onColorSelected Callback invoked when the user confirms their color selection.
 *                        Receives the selected color as an `AARRGGBB` integer.
 */
fun Context.showColorPickerDialog(
    @ColorInt initialColor: Int,
    onColorSelected: (Int) -> Unit,
) {
    val choreographer = Choreographer.getInstance()
    var dismissed = false
    ColorPickerDialog
        .Builder(this)
        .show {
            val onPositiveCallback =
                ColorEnvelopeListener { envelope, _ ->
                    envelope?.color?.let(onColorSelected)
                }
            // Use post() so setInitialColor() runs after the view is laid out.
            // This ensures the BrightnessSlideBar is fully initialized before applying
            // the initial color. Calling it too early causes the slider to use its
            // default position, resulting in an incorrect displayed color.
            colorPickerView.post { colorPickerView.setInitialColor(initialColor) }
            // Bubble showing the selected color
            val bubbleFlag = BubbleFlag(this@showColorPickerDialog)
            colorPickerView.flagView = bubbleFlag
            // TODO on these lower Android versions the view clipping fails and we end up with a
            //  square(standard view shape) instead of the expected circle so we update the
            //  positioning values so the square sis smaller and is centered in the background pin
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.R) {
                bubbleFlag
                    .findViewById<FrameLayout>(R.id.flag_color_layout_wrapper)
                    .updatePadding(
                        10.dp.toPx(this@showColorPickerDialog),
                        7.dp.toPx(this@showColorPickerDialog),
                        10.dp.toPx(this@showColorPickerDialog),
                    )
                bubbleFlag.alphaTileView.updateLayoutParams {
                    this.width = 12.dp.toPx(this@showColorPickerDialog)
                    this.height = 12.dp.toPx(this@showColorPickerDialog)
                }
            }
            setTitle(R.string.choose_color)
            setPositiveButton(R.string.dialog_ok, onPositiveCallback)
            negativeButton(R.string.dialog_cancel)
            setBottomSpace(12.dp)
            val callback =
                object : Choreographer.FrameCallback {
                    override fun doFrame(frameTimeNanos: Long) {
                        if (dismissed) return
                        colorPickerView.flagView.x =
                            (colorPickerView.selector.x + colorPickerView.selector.width / 2 - colorPickerView.flagView.width / 2)
                        val y = (colorPickerView.selector.y - colorPickerView.flagView.height)
                        if (y > 0) {
                            colorPickerView.flagView.y = y
                            colorPickerView.flagView.rotation = 0f
                        } else {
                            colorPickerView.flagView.y =
                                colorPickerView.selector.y + colorPickerView.flagView.height
                            colorPickerView.flagView.rotation = 180f
                        }
                        choreographer.postFrameCallback(this)
                    }
                }
            choreographer.postFrameCallback(callback)
        }.setOnDismissListener { dismissed = true }
}

/**
 * Custom flag view that displays the selected color in a bubble above the color picker selector.
 */
private class BubbleFlag(
    context: Context?,
) : FlagView(context, R.layout.view_colorpicker_flag_bubble) {
    val alphaTileView: AlphaTileView = findViewById(R.id.flag_color_layout)

    override fun onRefresh(colorEnvelope: ColorEnvelope) {
        alphaTileView.setPaintColor(colorEnvelope.color)
    }

    override fun onFlipped(isFlipped: Boolean?) {
    }
}

/**
 * Sets the margin of the bottom. this space is visible when [AlphaSlideBar] or
 * [BrightnessSlideBar] is attached.
 *
 * @param bottomSpace the bottom margin in display pixels.
 */
private fun ColorPickerDialog.Builder.setBottomSpace(bottomSpace: Dp) = this.setBottomSpace(bottomSpace.dp.toInt())
