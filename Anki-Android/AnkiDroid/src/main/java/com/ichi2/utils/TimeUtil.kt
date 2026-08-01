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

package com.ichi2.utils

import androidx.annotation.CheckResult
import com.ichi2.anki.common.time.TimeManager
import timber.log.Timber
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.time.TimeSource

/**
 * Measures the time it takes to execute [block] and logs the result as `Timber.d` on success
 * or `Timber.w` on failure.
 *
 * Exceptions are rethrown.
 *
 * Usage:
 *
 * ```kotlin
 * val result = measureTime("operation") { operation() }
 * ```
 * -> `D/TimeUtilKt executed htmlGenerator in 447.458us`
 */
@OptIn(ExperimentalContracts::class)
// 'inline fun' so logs use the correct context
inline fun <T> measureTime(
    methodName: String? = "",
    block: () -> T,
): T {
    contract {
        callsInPlace(block, InvocationKind.EXACTLY_ONCE)
    }
    val mark = TimeSource.Monotonic.markNow()
    var returnSuccess = true
    try {
        return block()
    } catch (e: Throwable) {
        returnSuccess = false
        throw e
    } finally {
        // elapsedNow can throw IllegalArgumentException; almost impossible practically
        runCatching { mark.elapsedNow() }.getOrNull()?.let { elapsed ->
            val functionName = if (methodName.isNullOrEmpty()) "" else "$methodName "

            if (returnSuccess) {
                Timber.d("executed %sin %s", functionName, elapsed)
            } else {
                Timber.w("executed %sin %s [FAILED]", functionName, elapsed)
            }
        }
    }
}

/**
 * Measures the time to execute a `suspend` function
 *
 * Usage:
 *
 * ```kotlin
 * val result = coMeasureTime("operation") { operation() }
 * ```
 * -> `D/TimeUtilKt executed mHtmlGenerator in 23ms`
 */
suspend fun <T> coMeasureTime(
    functionName: String? = "",
    function: suspend () -> T,
): T {
    val startTime = TimeManager.time.intTimeMS()
    val result = function()
    val endTime = TimeManager.time.intTimeMS()
    Timber.d(
        "executed %sin %dms",
        if (functionName.isNullOrEmpty()) "" else "$functionName ",
        endTime - startTime,
    )
    return result
}

/**
 * Used to time an operation across two function calls
 *
 * ```kotlin
 * private val renderStopwatch: Stopwatch = Stopwatch.init("page render")
 *
 * fun start() {
 *     renderStopwatch.reset()
 * }
 *
 * fun stop() {
 *     renderStopwatch.logElapsed()
 * }
 * ```
 *
 * -> `D/Stopwatch executed page render in 67ms`
 */
class Stopwatch(
    private val executionName: String?,
) {
    private var startTime = TimeManager.time.intTimeMS()

    fun logElapsed() {
        val endTime = TimeManager.time.intTimeMS()
        Timber.d(
            "executed %sin %dms",
            if (executionName.isNullOrEmpty()) "" else "$executionName ",
            endTime - startTime,
        )
    }

    fun reset() {
        startTime = TimeManager.time.intTimeMS()
    }

    companion object {
        /** initializes the stopwatch to ensure `stop()` before `start()` won't crash */
        @CheckResult
        fun init(executionName: String? = null) = Stopwatch(executionName)
    }
}
