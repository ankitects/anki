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
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/9600f033f745bfae4e00dd9fa43e44d3b30c22d2/qt/aqt/tts.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.common.coroutines.applicationScope
import com.ichi2.anki.i18n.normalize
import com.ichi2.anki.i18n.toAnkiTwoLetterCode
import com.ichi2.anki.libanki.TemplateManager
import com.ichi2.anki.libanki.TtsVoice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import java.util.Locale
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

    /** An immutable list of locales available for TTS */
    private lateinit var availableLocaleData: List<Locale>

    /** An immutable list of voices available for TTS */
    private lateinit var availableVoices: Set<AndroidTtsVoice>

    /** A job which populates [availableLocaleData] */
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

    /**
     * Returns the list of available locales for use in TTS
     *
     * This is a blocking function in the worst case scenario, but under all normal circumstances
     * this should return instantly
     *
     * @return The list of available languages, or an empty list if an error occurred
     */
    @Deprecated("blocking function", replaceWith = ReplaceWith("availableLocales"))
    fun availableLocalesBlocking(): List<Locale> {
        if (this::availableLocaleData.isInitialized) {
            return this.availableLocaleData
        }
        Timber.w("availableLocales was unexpectedly a blocking function")

        launchBuildLocalesJob()
        runBlocking {
            buildLocalesJob?.join()
        }

        return this.availableLocaleData
    }

    suspend fun refresh() {
        launchBuildLocalesJob()
        buildLocalesJob?.join()
        loadTtsVoicesData()
    }

    /**
     * Returns the list of available locales for use in TTS
     *
     * Under all normal circumstances this should return instantly
     *
     * @return The list of available languages, or an empty list if an error occurred
     */
    private suspend fun availableLocales(): List<Locale> {
        if (this::availableLocaleData.isInitialized) {
            return this.availableLocaleData
        }

        launchBuildLocalesJob()
        buildLocalesJob?.join()

        return this.availableLocaleData
    }

    /**
     * Returns the list of available voices for use in TTS
     */
    suspend fun allTtsVoices(): Set<AndroidTtsVoice> {
        if (this::availableLocaleData.isInitialized) {
            return this.availableVoices
        }

        launchBuildLocalesJob()
        buildLocalesJob?.join()
        return this.availableVoices
    }

    /**
     * Launches a [Job] to populate the list of available locales for use in TTS
     *
     * This is run in the background, without blocking the main thread
     *
     * [availableLocales] awaits the result of this function
     */
    fun launchBuildLocalesJob() {
        if (this::availableLocaleData.isInitialized || buildLocalesJob != null) {
            Timber.d("job already started")
            return
        }

        Timber.d("launching job")
        // This is intended to be a global singleton outside the lifecycle of a specific activity
        // Most of the time of execution is waiting for the TTS Engine to initialize
        buildLocalesJob =
            applicationScope.launch(Dispatchers.IO) {
                Timber.d("executing job")
                loadTtsVoicesData()
                buildLocalesJob = null
                Timber.d("%d TTS Voices available", availableLocaleData.size)
            }
    }

    /**
     * Populates [availableVoices] and [availableLocaleData] with the voices and locales available
     * across every installed TTS engine (#18737), not just the user's default engine.
     */
    private suspend fun loadTtsVoicesData() {
        // A default-engine instance is needed first to enumerate the installed engines
        val probeTts = createTts()
        if (probeTts == null) {
            Timber.e("Unable to build list of TTS Voices")
            availableVoices = emptySet()
            availableLocaleData = emptyList()
            return
        }

        val enginePackages =
            try {
                // `engines` lists every installed engine; include the default defensively
                (probeTts.engines.map { it.name } + listOfNotNull(probeTts.defaultEngine)).distinct()
            } catch (e: Exception) {
                Timber.w(e, "unable to list TTS engines")
                listOfNotNull(probeTts.defaultEngine)
            } finally {
                probeTts.shutdown()
            }

        val (voices, locales) = loadVoicesFromEngines(enginePackages) { engine -> createTts(engine) }
        availableVoices = voices
        availableLocaleData = locales
    }

    /**
     * Loads the voices and locales available across the provided [enginePackages].
     *
     * Each engine is initialised independently so a single misbehaving engine cannot prevent the
     * others from being listed.
     *
     * @param createTts builds a [TextToSpeech] bound to the provided engine package, or `null` on failure
     * @return the union of all voices, and the union of all normalized locales, across [enginePackages]
     */
    internal suspend fun loadVoicesFromEngines(
        enginePackages: List<String>,
        createTts: suspend (engine: String) -> TextToSpeech?,
    ): Pair<Set<AndroidTtsVoice>, List<Locale>> {
        val voices = mutableSetOf<AndroidTtsVoice>()
        val locales = mutableSetOf<Locale>()
        for (engine in enginePackages) {
            val tts = createTts(engine)
            if (tts == null) {
                Timber.w("Unable to initialize TTS engine: %s", engine)
                continue
            }
            // Samsung TextToSpeech engine returns locales with a displayName of "GBR,DEFAULT"/"GBR,f00"
            // so normalize them before displaying them to users
            // sample of problematic data: language = "eng", region = "GBR", variant = "f00"
            try {
                tts.voices?.let { engineVoices ->
                    voices += engineVoices.map { it.toTtsVoice(engine) }
                }
                tts.availableLanguages?.let { engineLocales ->
                    locales += engineLocales.map { it.normalize() }
                }
            } catch (e: Exception) {
                Timber.w(e, "error reading voices from TTS engine: %s", engine)
            } finally {
                tts.shutdown()
            }
        }
        return voices to locales.toList()
    }

    /**
     * Creates a usable instance of a [TextToSpeech] as a `suspend` function
     *
     * @param engine the package name of the TTS engine to use, or `null` to use the user's
     * default engine
     * @return a usable [TextToSpeech] instance, or `null` if the [TextToSpeech.OnInitListener]
     * returns [TextToSpeech.ERROR]
     */
    suspend fun createTts(engine: String? = null) =
        suspendCancellableCoroutine { continuation ->
            var textToSpeech: TextToSpeech? = null
            continuation.invokeOnCancellation {
                Timber.v("TTS creation cancelled")
                textToSpeech?.stop()
                textToSpeech?.shutdown()
            }
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
                if (engine == null) {
                    TextToSpeech(appContext, onInit)
                } else {
                    TextToSpeech(appContext, onInit, engine)
                }
        }
}

/**
 * `{{tts-voices:}}` A filter which lists all available TTS Voices for the current engine
 */
class TtsVoicesFieldFilter : TemplateManager.FieldFilter() {
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
