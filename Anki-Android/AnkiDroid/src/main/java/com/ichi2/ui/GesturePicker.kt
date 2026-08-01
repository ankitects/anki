/*
 *  Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
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
import android.hardware.SensorManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.ichi2.anki.R
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.cardviewer.GestureListener
import com.ichi2.anki.databinding.ViewGesturePickerBinding
import com.ichi2.anki.dialogs.WarningDisplay
import com.ichi2.utils.UiUtil.setSelectedValue
import com.squareup.seismic.ShakeDetector
import timber.log.Timber

// This class exists as elements resized when adding in the spinner to GestureDisplay.kt

/** [View] which allows selection of a gesture either via taps/swipes, or via a [Spinner]
 * The spinner aids discoverability of [Gesture.DOUBLE_TAP]
 * as it is not explained in [GestureDisplay].
 */
open class GesturePicker(
    ctx: Context,
    attributeSet: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : ConstraintLayout(ctx, attributeSet, defStyleAttr),
    WarningDisplay,
    ShakeDetector.Listener {
    protected val binding: ViewGesturePickerBinding
    override val warningTextView get() = binding.warning

    private var onGestureListener: GestureListener? = null
    private var shakeDetector: ShakeDetector? = null
    private val sensorManager get() = ContextCompat.getSystemService(context, SensorManager::class.java)

    init {
        val inflater = ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        binding = ViewGesturePickerBinding.inflate(inflater, this, true)
        binding.gestureDisplay.setGestureChangedListener(this::onGesture)
        binding.gestureSpinner.adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item, allGestures())
        binding.gestureSpinner.onItemSelectedListener = InnerSpinner()
    }

    fun getGesture() = binding.gestureDisplay.getGesture()

    private fun onGesture(gesture: Gesture?) {
        Timber.d("gesture: %s", gesture?.toDisplayString(context))

        setGesture(gesture)

        if (gesture == null) {
            return
        }

        onGestureListener?.onGesture(gesture)
    }

    private fun setGesture(gesture: Gesture?) {
        binding.gestureSpinner.setSelectedValue(GestureWrapper(gesture))
        binding.gestureDisplay.setGesture(gesture)
    }

    /** Not fired if deselected */
    fun setGestureChangedListener(listener: GestureListener?) {
        onGestureListener = listener
    }

    private fun allGestures(): List<GestureWrapper> = (listOf(null) + availableGestures()).map(this::GestureWrapper).toList()

    protected open fun availableGestures() = binding.gestureDisplay.availableValues()

    inner class GestureWrapper(
        val gesture: Gesture?,
    ) {
        override fun toString(): String = gesture?.toDisplayString(context) ?: resources.getString(R.string.gestures_none)

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as GestureWrapper

            return gesture == other.gesture
        }

        override fun hashCode() = gesture?.hashCode() ?: 0
    }

    private inner class InnerSpinner : AdapterView.OnItemSelectedListener {
        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            val wrapper = parent?.getItemAtPosition(position) as? GestureWrapper
            onGesture(wrapper?.gesture)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        startShakeDetector()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        shakeDetector?.stop()
    }

    private fun startShakeDetector() {
        shakeDetector =
            ShakeDetector(this).also {
                it.start(sensorManager, SensorManager.SENSOR_DELAY_UI)
            }
    }

    override fun hearShake() {
        Timber.d("Shake detected in GesturePicker")
        onGesture(Gesture.SHAKE)
    }
}
