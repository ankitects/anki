/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
import android.util.AttributeSet
import com.google.android.material.button.MaterialButton
import com.ichi2.anki.R
import kotlin.math.roundToInt

/**
 * A custom button for the eraser tool that manages its own UI state.
 * It updates its icon and text based on the current eraser mode and stroke width.
 */
class EraserButton : MaterialButton {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, com.google.android.material.R.attr.materialButtonStyle)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        isCheckable = true
    }

    /**
     * Updates the button's visual state based on the eraser's properties.
     *
     * @param isChecked Whether the eraser tool is currently active.
     * @param mode The current eraser mode (PIXEL or PATH).
     * @param strokeWidth The current eraser stroke width to display.
     */
    fun updateState(
        isChecked: Boolean,
        mode: EraserMode,
        strokeWidth: Float,
    ) {
        this.isChecked = isChecked
        text = strokeWidth.roundToInt().toString()
        when (mode) {
            EraserMode.STROKE -> setIconResource(R.drawable.ic_edit_off)
            EraserMode.INK -> setIconResource(R.drawable.eraser)
        }
    }
}
