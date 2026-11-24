// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import com.ichi2.anki.TtsVoices
import kotlinx.coroutines.CompletableDeferred
import org.robolectric.util.ReflectionHelpers
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference

/**
 * Seeds the reviewer language cache without waiting for unfinished startup discovery from earlier tests.
 * Use with [use] to restore the previous cache when the test completes or fails.
 */
class TtsVoicesCacheOverride(
    engine: String?,
    locales: List<Locale>,
) : AutoCloseable {
    private val cache =
        ReflectionHelpers.getStaticField<AtomicReference<CompletableDeferred<TtsVoices.EngineLocales>>>(
            TtsVoices::class.java,
            "engineLocaleData",
        )
    private val previous = cache.getAndSet(CompletableDeferred(TtsVoices.EngineLocales(engine, locales)))

    override fun close() {
        cache.set(previous)
    }
}
