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
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.LinearLayout
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ViewAxisDisplayBinding
import com.ichi2.anki.reviewer.Axis
import com.ichi2.anki.reviewer.Binding
import timber.log.Timber

/**
 * Displays live values of an [Axis] (joystick/trigger), and allows selection of a binding if an
 * [extremity][AxisValueDisplay.isExtremity] has been received
 *
 * The [axisName][ViewAxisDisplayBinding.axisName] of the Axis (AXIS_X)
 *
 * The [value] of the Axis [-1, 1]
 *   - If a value hits an extremity, the display changes color. See [AxisValueDisplay]
 *
 * Two buttons: [selectMinExtremity][ViewAxisDisplayBinding.selectMinExtremity] and
 *  [selectMaxExtremity][ViewAxisDisplayBinding.selectMaxExtremity]
 *   - If an [extremity][AxisValueDisplay.isExtremity] is reached, these are activated
 *   - Calls [onExtremitySelectedListener] if tapped
 *
 * @see R.layout.axis_display
 * @see AxisValueDisplay
 */
class AxisSelector : LinearLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int) : super(
        context,
        attrs,
        defStyle,
    )

    private val binding = ViewAxisDisplayBinding.inflate(LayoutInflater.from(context), this, true)

    private var onExtremitySelectedListener: ((Binding.AxisButtonBinding) -> Unit)? = null

    init {
        // Disabling buttons ensures that a user cannot map a negative value on a unidirectional
        // axis, such as the triggers on my 8BitDo
        // It's hard to know if an axis is single, or multidimensional until we've received input
        binding.axisDisplay.setExtremityListener { valueAtExtremity ->
            when (valueAtExtremity) {
                -1f -> enableMinButton()
                1f -> enableMaxButton()
            }
        }

        // call onExtremitySelectedListener
        binding.selectMinExtremity.setOnClickListener { selectMinimumValue() }
        binding.selectMaxExtremity.setOnClickListener { selectMaximumValue() }
    }

    private fun selectMaximumValue() {
        Timber.i("%s: max pressed", axis)
        onExtremitySelectedListener?.invoke(Binding.AxisButtonBinding(axis!!, 1f))
    }

    private fun selectMinimumValue() {
        Timber.i("%s: min pressed", axis)
        onExtremitySelectedListener?.invoke(Binding.AxisButtonBinding(axis!!, -1f))
    }

    private fun enableMaxButton() {
        if (binding.selectMaxExtremity.isEnabled) return
        Timber.i("%s: max button enabled", axis)
        binding.selectMaxExtremity.isEnabled = true
    }

    private fun enableMinButton() {
        if (binding.selectMinExtremity.isEnabled) return
        Timber.i("%s: min button enabled", axis)
        binding.selectMinExtremity.isEnabled = true
    }

    /**
     * The [Axis] that this control applies to
     *
     * should be lateinit + non-null
     */
    var axis: Axis? = null
        set(value) {
            field = value
            text = value.toString()
        }

    /** The name of the axis */
    var text: String
        get() = binding.axisName.text.toString()
        private set(value) {
            binding.axisName.text = value
        }

    var value: Float by binding.axisDisplay::value

    fun setOnExtremitySelectedListener(listener: ((Binding.AxisButtonBinding) -> Unit)?) {
        onExtremitySelectedListener = listener
    }
}
