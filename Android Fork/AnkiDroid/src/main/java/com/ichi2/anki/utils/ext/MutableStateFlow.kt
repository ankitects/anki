// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.utils.ext

import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.reflect.KProperty

/**
 * Syntactic sugar to expose a [MutableStateFlow] as a `var`
 *
 * ```kotlin
 * var flow = savedStateHandle.getMutableStateFlow<String>("tag", "default")
 * var prop by flow.asVar()
 * ```
 */
fun <T> MutableStateFlow<T>.asVar(): StateFlowVarDelegate<T> = StateFlowVarDelegate(this)

class StateFlowVarDelegate<T>(
    private val flow: MutableStateFlow<T>,
) {
    operator fun getValue(
        thisRef: Any?,
        property: KProperty<*>,
    ): T = flow.value

    operator fun setValue(
        thisRef: Any?,
        property: KProperty<*>,
        value: T,
    ) {
        flow.value = value
    }
}
