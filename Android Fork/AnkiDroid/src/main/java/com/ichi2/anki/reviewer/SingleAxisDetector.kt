// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.Context
import android.view.MotionEvent

data class SingleAxisDetector<B : MappableBinding, A : MappableAction<B>>(
    private val action: A,
    val mappableBinding: B,
) {
    private val axis: Axis? = (mappableBinding.binding as? Binding.AxisButtonBinding)?.axis
    private val threshold: Float = (mappableBinding.binding as? Binding.AxisButtonBinding)?.threshold ?: 0F

    /** If the command has been executed and we have not returned lower than the threshold */
    private var sentAction: Boolean = false

    /**
     * If multiple events above the threshold are obtained, only return 1 command
     * until the value is under the threshold
     */
    private val debouncedAction: A?
        get() {
            if (sentAction) return null
            sentAction = true
            return action
        }

    /**
     * Given a [MotionEvent], determine whether we've reached [threshold].
     *
     * If we have, and [action] has not been sent in the period that we reached the threshold
     * then send the command once and wait for the value to go back under the threshold
     *
     * @return [action] or `null`
     */
    fun getAction(ev: MotionEvent): A? {
        // TODO: We may need to handle historical events as well
        val value = ev.getAxisValue(axis?.motionEventValue ?: MotionEvent.INVALID_POINTER_ID)
        when {
            threshold > 0 -> {
                if (value >= threshold) return debouncedAction
            }
            threshold < 0 -> {
                if (value <= threshold) return debouncedAction
            }
        }
        sentAction = false
        return null
    }

    fun toDisplayString(context: Context) = mappableBinding.toDisplayString(context)
}
