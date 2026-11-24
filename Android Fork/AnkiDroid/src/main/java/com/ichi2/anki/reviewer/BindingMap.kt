// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.reviewer

import android.content.SharedPreferences
import android.view.KeyEvent
import android.view.MotionEvent
import com.ichi2.anki.cardviewer.Gesture
import com.ichi2.anki.reviewer.Binding.Companion.possibleKeyBindings

/**
 * Maps the given [MappableAction]s with their configured [MappableBinding]s.
 *
 * That way, key presses and joystick movements can be detected to trigger their actions.
 *
 * * [onKeyDown]: captures key presses
 * * [onGenericMotionEvent]: captures joystick/pedal input. Axes can either be bidirectional,
 *   or unidirectional.
 */
class BindingMap<B : MappableBinding, A : MappableAction<B>>(
    sharedPrefs: SharedPreferences,
    actions: List<A>,
    private var processor: BindingProcessor<B, A>? = null,
) {
    private val keyMap = HashMap<Binding, List<Pair<A, B>>>()
    private val axisDetectors: List<SingleAxisDetector<B, A>>
    private val gestureMap = HashMap<Gesture, List<Pair<A, B>>>()

    init {
        val axisList = mutableListOf<SingleAxisDetector<B, A>>()
        for (action in actions) {
            val mappableBindings = action.getBindings(sharedPrefs)
            for (mappableBinding in mappableBindings) {
                when (val binding = mappableBinding.binding) {
                    is Binding.KeyBinding -> {
                        if (binding in keyMap) {
                            (keyMap[binding] as MutableList).add(action to mappableBinding)
                        } else {
                            keyMap[binding] = mutableListOf(action to mappableBinding)
                        }
                    }
                    is Binding.AxisButtonBinding -> {
                        axisList.add(SingleAxisDetector(action, mappableBinding))
                    }
                    is Binding.GestureInput -> {
                        if (binding.gesture in gestureMap) {
                            (gestureMap[binding.gesture] as MutableList).add(action to mappableBinding)
                        } else {
                            gestureMap[binding.gesture] = mutableListOf(action to mappableBinding)
                        }
                    }
                }
            }
        }
        axisDetectors = axisList.toList()
    }

    fun onKeyDown(event: KeyEvent): Boolean {
        if (event.repeatCount > 0) {
            return false
        }
        val bindings = possibleKeyBindings(event)
        for (binding in bindings) {
            val actionAndMappableBindings = keyMap[binding] ?: continue
            for ((action, mappableBinding) in actionAndMappableBindings) {
                if (processor?.processAction(action, mappableBinding) == true) return true
            }
        }
        return false
    }

    /**
     * Accepts a [MotionEvent] and determines if one or more commands need to be executed
     * @return whether one or more commands were executed
     */
    fun onGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (ev == null || axisDetectors.isEmpty()) return false

        var processed = false
        for (detector in axisDetectors) {
            val action = detector.getAction(ev) ?: continue
            processed = true
            processor?.processAction(action, detector.mappableBinding)
        }
        return processed
    }

    fun onGesture(gesture: Gesture): Boolean {
        val mappableBindings = gestureMap[gesture] ?: return false
        for ((action, mappableBinding) in mappableBindings) {
            if (processor?.processAction(action, mappableBinding) == true) return true
        }
        return false
    }

    fun isBound(gesture: Gesture): Boolean = gesture in gestureMap
}
