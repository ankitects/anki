/*
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
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/9600f033f745bfae4e00dd9fa43e44d3b30c22d2/qt/aqt/tts.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.i18n.normalize
import com.ichi2.anki.i18n.toAnkiTwoLetterCode
import com.ichi2.anki.libanki.TemplateManager
import com.ichi2.anki.libanki.TtsVoice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.Locale
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.resume

/**
 * Voices which an be used in TTS (Text to Speech)
 *
 * This is a singleton: it requires the TTS Engine to build, which can take multiple
 * seconds to initialise. This moves initialisation to a background thread on app startup
 *
 * In addition, the list of available TTS Voices shouldn't change during execution
 */
object TtsVoices {
    // A new instance of this list is not required if the app language changes: .displayName returns
    // the new values

    /** Published before scanning other engines, so legacy TTS need not wait for them. */
    private val engineLocaleData = AtomicReference(CompletableDeferred<EngineLocales>())

    /** Voices in engine-preference order: the default engine first, then the other engines. */
    private lateinit var availableVoices: List<AndroidTtsVoice>

    /** A job which populates both the default-engine languages and all available voices. */
    private var buildLocalesJob: Job? = null

    /**
     * The package name of the default speech synthesis engine.
     *
     * @return Package name of the Tts engine that the user has chosen as their default.
     * 'null' if the system has no engine or if an error occurs
     *
     * @see TextToSpeech.getDefaultEngine
     */
    var ttsEngine: String? = null
        private set

    /** Returns whether the system has any installed TTS services. */
    fun hasInstalledEngine(context: Context): Boolean {
        val engines =
            context.packageManager.queryIntentServices(
                Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE),
                PackageManager.MATCH_DEFAULT_ONLY,
            )
        return engines.isNotEmpty()
    }

    suspend fun refresh() {
        launchBuildLocalesJob()
        buildLocalesJob?.join()
        val defaultLocales = CompletableDeferred<EngineLocales>()
        availableVoices = loadVoices(defaultLocales, ::createTts)
        engineLocaleData.set(defaultLocales)
    }

    /**
     * Returns cached languages for the reviewer's engine, suspending until ready.
     * If the default engine has changed since startup, query that engine on a separate connection.
     */
    internal suspend fun localesForEngine(engine: String?): List<Locale> {
        launchBuildLocalesJob()
        val cached = engineLocaleData.get()
        val result = cached.await()
        if (result.engine == engine) return result.locales

        val locales =
            withContext(ioDispatcher) {
                val tts = createTts(engine) ?: return@withContext emptyList()
                try {
                    readLocales(tts)
                } finally {
                    tts.shutdown()
                }
            }
        // A refresh may have published newer data while this query was in flight.
        engineLocaleData.compareAndSet(cached, CompletableDeferred(EngineLocales(engine, locales)))
        return locales
    }

    /**
     * Returns distinct voices from the default engine first, then other engines in discovery order.
     * Voice order within each engine is preserved. Playback uses this order for language fallback.
     */
    suspend fun allTtsVoices(): List<AndroidTtsVoice> {
        if (this::availableVoices.isInitialized) {
            return this.availableVoices
        }

        launchBuildLocalesJob()
        buildLocalesJob?.join()
        return this.availableVoices
    }

    /**
     * Launches a [Job] to populate the default-engine languages and all available voices
     *
     * This is run in the background, without blocking the main thread
     *
     * Legacy TTS does not wait for the full voice scan.
     */
    fun launchBuildLocalesJob() {
        if (this::availableVoices.isInitialized || buildLocalesJob != null) {
            Timber.d("job already started")
            return
        }

        Timber.d("launching job")
        // This is intended to be a global singleton outside the lifecycle of a specific activity
        // Most of the time of execution is waiting for the TTS Engine to initialize
        val defaultLocales = engineLocaleData.get()
        buildLocalesJob =
            applicationScope.launch(Dispatchers.IO) {
                Timber.d("executing job")
                availableVoices = loadVoices(defaultLocales, ::createTts)
                buildLocalesJob = null
                Timber.d("%d TTS Voices available", availableVoices.size)
            }
    }

    /**
     * Publishes default-engine languages before collecting voices from every installed engine.
     * Both queries run in the startup job's IO context.
     */
    internal suspend fun loadVoices(
        defaultLocales: CompletableDeferred<EngineLocales>,
        createTts: suspend (String?) -> TextToSpeech?,
    ): List<AndroidTtsVoice> {
        // A default-engine instance is needed first to enumerate the installed engines
        val probeTts =
            try {
                createTts(null)
            } catch (e: Exception) {
                // Do not leave a reviewer waiting forever if discovery fails before publishing languages.
                defaultLocales.completeExceptionally(e)
                throw e
            }
        if (probeTts == null) {
            Timber.e("Unable to build list of TTS Voices")
            defaultLocales.complete(EngineLocales(null, emptyList()))
            return emptyList()
        }

        val enginePackages =
            try {
                val engine = probeTts.defaultEngine
                defaultLocales.complete(EngineLocales(engine, readLocales(probeTts)))
                // Put the default first, preserving discovery order for the remaining engines.
                try {
                    (listOfNotNull(engine) + probeTts.engines.map { it.name }).distinct()
                } catch (e: Exception) {
                    Timber.w(e, "unable to list TTS engines")
                    listOfNotNull(engine)
                }
            } catch (e: Exception) {
                defaultLocales.completeExceptionally(e)
                throw e
            } finally {
                probeTts.shutdown()
            }

        return loadVoicesFromEngines(enginePackages) { engine -> createTts(engine) }
    }

    internal data class EngineLocales(
        val engine: String?,
        val locales: List<Locale>,
    )

    private fun readLocales(tts: TextToSpeech): List<Locale> =
        try {
            // Normalize engine-provided locales before displaying them, then remove aliases.
            tts.availableLanguages
                .orEmpty()
                .map { it.normalize() }
                .distinct()
        } catch (e: Exception) {
            Timber.w(e, "unable to read TTS languages")
            emptyList()
        }

    /**
     * Loads the voices available across the provided [enginePackages].
     *
     * Each engine is initialised independently so a single misbehaving engine cannot prevent the
     * others from being listed.
     * Voices are distinct by engine and voice name, keeping the first occurrence.
     *
     * @param createTts builds a [TextToSpeech] bound to the provided engine package, or `null` on failure
     * @return all voices in [enginePackages] order, preserving each engine's voice order
     */
    internal suspend fun loadVoicesFromEngines(
        enginePackages: List<String>,
        createTts: suspend (engine: String) -> TextToSpeech?,
    ): List<AndroidTtsVoice> {
        val voices = mutableListOf<AndroidTtsVoice>()
        for (engine in enginePackages) {
            val tts = createTts(engine)
            if (tts == null) {
                Timber.w("Unable to initialize TTS engine: %s", engine)
                continue
            }
            try {
                tts.voices?.let { engineVoices ->
                    voices += engineVoices.map { it.toTtsVoice(engine) }
                }
            } catch (e: Exception) {
                Timber.w(e, "error reading voices from TTS engine: %s", engine)
            } finally {
                tts.shutdown()
            }
        }
        return voices.distinctBy { it.engine to it.voice.name }
    }

    /**
     * Creates a usable instance of a [TextToSpeech] as a `suspend` function
     *
     * @param engine the package name of the TTS engine to use, or `null` to use the user's
     * default engine
     * @return a usable [TextToSpeech] instance, or `null` if the [TextToSpeech.OnInitListener]
     * returns [TextToSpeech.ERROR] or construction fails
     * @throws CancellationException if creation is cancelled
     */
    suspend fun createTts(engine: String? = null) =
        suspendCancellableCoroutine { continuation ->
            var textToSpeech: TextToSpeech? = null
            Timber.v("begin TTS creation (engine: %s)", engine)
            val onInit =
                TextToSpeech.OnInitListener { status ->
                    if (status == TextToSpeech.SUCCESS) {
                        Timber.v("TTS creation success (engine: %s)", engine)
                        // `ttsEngine` tracks the user's default engine only
                        if (engine == null) {
                            ttsEngine = textToSpeech?.defaultEngine
                        }
                        continuation.resume(textToSpeech)
                    } else {
                        Timber.e("TTS creation failed. status: %d (engine: %s)", status, engine)
                        textToSpeech?.shutdown()
                        continuation.resume(null)
                    }
                }
            // TextToSpeech retains the context. So we can't give it any context that
            // may be expected to disappear, as it would cause a memory leak. Hence
            // we pass it the application as context.
            textToSpeech =
                try {
                    if (engine == null) {
                        TextToSpeech(appContext, onInit)
                    } else {
                        TextToSpeech(appContext, onInit, engine)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.w(e, "Unable to initialize TTS engine: %s", engine)
                    continuation.resume(null)
                    null
                }
            // Register after construction so cancellation cannot miss the new instance.
            continuation.invokeOnCancellation {
                Timber.v("TTS creation cancelled")
                // Engine calls can block.
                // Launch on a scope which survives cancellation of the activity.
                applicationScope.launch(ioDispatcher) {
                    textToSpeech?.stop()
                    textToSpeech?.shutdown()
                }
            }
        }
}

/**
 * `{{tts-voices:}}` A filter which lists all available TTS Voices for the current engine
 */
class TtsVoicesFieldFilter : TemplateManager.FieldFilter {
    // modified from libAnki: tts.py: on_tts_voices
    override fun apply(
        fieldText: String,
        fieldName: String,
        filterName: String,
        ctx: TemplateManager.TemplateRenderContext,
    ): String {
        if (filterName != "tts-voices") {
            return fieldText
        }
        // This is not translated in Anki Desktop
        return "<a href=\"tts-voices:\"/>Open TTS voices settings</a>"
    }

    companion object {
        /** Enables the {{tts-voices}} filter */
        fun ensureApplied() {
            TemplateManager.fieldFilters.putIfAbsent("tts-voices", TtsVoicesFieldFilter())
        }
    }
}

/**
 * Converts a [Voice] to a [TtsVoice] for use in libAnki
 *
 * @param engine The package name of the TTS Engine
 */
fun Voice.toTtsVoice(engine: String) = AndroidTtsVoice(this, engine)

// We include the engine name in the TTS 'name' to future-proof the feature of
// allowing a user to switch between TTS providers on the same card
// a name looks like: com.google.android.tts-cmn-cn-x-ccc-local
// com.google.android.tts + cmn-cn-x-ccc-local

/**
 * An instance of [TtsVoice] which allows access to the underlying [Voice] object
 */
class AndroidTtsVoice(
    val voice: Voice,
    val engine: String,
) : TtsVoice(name = "$engine-${voice.name}", lang = voice.locale.toAnkiTwoLetterCode()) {
    override fun unavailable(): Boolean = voice.features.contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)

    /**
     * The locale of the voice normalized to a human readable language/country, missing the variant
     * Designed for [Locale.getDisplayName]
     */
    val normalizedLocale: Locale
        // on Samsung phones, the variant (f001/DEFAULT) looks awful in the UI
        // normalise: "en-GBR" is "English (GBR)". "en-GB" is "English (United Kingdom)"
        // then remove the variant: We want English (United Kingdom), not (United Kingdom,DEFAULT)
        get() = voice.locale.normalize().let { Locale.forLanguageTag(it.language + '-' + it.country) }

    val isNetworkConnectionRequired
        get() = voice.isNetworkConnectionRequired
}
