// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.coroutines

import kotlin.coroutines.cancellation.CancellationException

/**
 * Adds the call site to exceptions raised in [block].
 *
 * In helpers (`withCol`), the helpers should be made `inline`, so it traces the caller and not
 * the helper.
 *
 * **Usage**
 * ```kotlin
 * suspend inline fun readDecksWithTrace() = recordCoroutineCaller {
 *     withContext(Dispatchers.IO) { readDecks() }
 * }
 * ```
 *
 * This is added as a [suppressed call site][Exception.addSuppressed].
 *
 * Do not depend on this to add the full call stack, only the immediate caller.
 */
@Suppress("RedundantSuspendModifier") // Intentionally restricted to suspending contexts.
suspend inline fun <T> recordCoroutineCaller(block: () -> T): T =
    try {
        block()
    } catch (exception: CancellationException) {
        throw exception
    } catch (exception: Exception) {
        exception.addSuppressed(Exception("Coroutine call site"))
        throw exception
    }
