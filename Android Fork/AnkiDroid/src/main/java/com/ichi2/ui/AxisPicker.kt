// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.ichi2.anki.databinding.DialogAxisPickerBinding
import com.ichi2.anki.reviewer.Axis
import com.ichi2.anki.reviewer.Binding
import timber.log.Timber

/**
 * Listens to [joystick input][View.onGenericMotionEvent], if motion is detected on an [Axis],
 * show the axis as selectable and mappable to a [Binding.AxisButtonBinding]
 *
 * If an Axis has two extremities, a user may select one of these.
 *
 * @see AxisSelector
 */
class AxisPicker : ConstraintLayout {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    val binding = DialogAxisPickerBinding.inflate(LayoutInflater.from(context))

    /** Maps from an [Axis] to the [AxisSelector] displaying + allowing selection of it */
    private val axisMap = mutableMapOf<Axis, AxisSelector>()

    private var onBindingChangedListener: ((Binding) -> Unit)? = null

    fun setBindingChangedListener(listener: (Binding) -> Unit) {
        onBindingChangedListener = listener
    }

    init {
        binding.root.requestFocus()
        binding.root.setOnGenericMotionListener { _, event -> handleMotionEvent(event) }
    }

    @Suppress("SameReturnValue")
    private fun handleMotionEvent(event: MotionEvent?): Boolean {
        if (event == null) return true
        Timber.w("handleMotionEvent")

        for (axis in Axis.entries) {
            val axisValue = event.getAxisValue(axis.motionEventValue)
            // if we've NEVER had a non-zero value, ignore it
            if (axisValue == 0f && !axisMap.containsKey(axis)) continue

            // if we have a non-zero value, create the view if it doesn't already exist
            val axisSelector = getOrCreateAxisSelector(axis)
            // and update its value
            axisSelector.value = axisValue
        }

        return true
    }

    private fun getOrCreateAxisSelector(axis: Axis): AxisSelector {
        // if we've already added the control, return it
        axisMap[axis]?.let { return it }

        // when adding the first control, we want to make the
        // available axes visible, so the user can see their current values
        binding.availableAxesContainer.visibility = VISIBLE
        // we also want to hide the TextView, but we can't make it invisible as it's
        // providing our input events. Blanking the text has the same effect
        binding.selectedAxisTextView.text = ""

        // setup & return the control
        return AxisSelector(binding.root.context).also { view ->
            view.axis = axis
            view.setOnExtremitySelectedListener { binding ->
                Timber.d("selected binding %s", binding)
                onBindingChangedListener?.invoke(binding)
            }

            axisMap[axis] = view
            binding.availableAxes.addView(view)
        }
    }
}
