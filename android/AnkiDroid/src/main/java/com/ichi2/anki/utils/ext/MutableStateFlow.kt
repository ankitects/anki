/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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
