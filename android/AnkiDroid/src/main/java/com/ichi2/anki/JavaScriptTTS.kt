/*
 * Copyright (c) 2021 mikunimaru <com.mikuni0@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki

import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.OnInitListener
import androidx.annotation.IntDef
import com.ichi2.anki.common.android.appContext

/**
 * Since it is assumed that only advanced users will use the JavaScript api,
 * here, Android's TextToSpeech is converted for JavaScript almost as it is, giving priority to free behavior.
 * https://developer.android.com/reference/android/speech/tts/TextToSpeech
 */
class JavaScriptTTS internal constructor() : OnInitListener {
    @IntDef(TTS_SUCCESS, TTS_ERROR)
    annotation class ErrorOrSuccess

    @IntDef(TTS_QUEUE_ADD, TTS_QUEUE_FLUSH)
    annotation class QueueMode

    @IntDef(TTS_LANG_AVAILABLE, TTS_LANG_COUNTRY_AVAILABLE, TTS_LANG_COUNTRY_VAR_AVAILABLE, TTS_LANG_MISSING_DATA, TTS_LANG_NOT_SUPPORTED)
    annotation class TTSLangResult

    /** OnInitListener method to receive the TTS engine status  */
    override fun onInit(
        @ErrorOrSuccess status: Int,
    ) {
        ttsOk = status == TextToSpeech.SUCCESS
    }

    /**
     * A method to speak something
     * @param text Content to speak
     * @param queueMode 1 for QUEUE_ADD and 0 for QUEUE_FLUSH.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    fun speak(
        text: String?,
        @QueueMode queueMode: Int,
    ): Int = tts.speak(text, queueMode, ttsParams, "stringId")

    /**
     * If only a string is given, set QUEUE_FLUSH to the default behavior.
     * @param text Content to speak
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    fun speak(text: String?): Int = tts.speak(text, TextToSpeech.QUEUE_FLUSH, ttsParams, "stringId")

    /**
     * Sets the text-to-speech language.
     * The TTS engine will try to use the closest match to the specified language as represented by the Locale, but there is no guarantee that the exact same Locale will be used.
     * @param loc Specifying the language to speak
     * @return 0 Denotes the language is available for the language by the locale, but not the country and variant.
     *     <li> 1 Denotes the language is available for the language and country specified by the locale, but not the variant.
     *     <li> 2 Denotes the language is available exactly as specified by the locale.
     *     <li> -1 Denotes the language data is missing.
     *     <li> -2 Denotes the language is not supported.
     */
    @TTSLangResult
    fun setLanguage(loc: String): Int {
        // The Int values will be returned
        // Code indicating the support status for the locale. See LANG_AVAILABLE, LANG_COUNTRY_AVAILABLE, LANG_COUNTRY_VAR_AVAILABLE, LANG_MISSING_DATA and LANG_NOT_SUPPORTED.
        return tts.setLanguage(LanguageUtils.localeFromStringIgnoringScriptAndExtensions(loc))
    }

    /**
     * Sets the speech pitch for the TextToSpeech engine. This has no effect on any pre-recorded speech.
     * @param pitch float: Speech pitch. 1.0 is the normal pitch, lower values lower the tone of the synthesized voice, greater values increase it.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    fun setPitch(pitch: Float): Int {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return tts.setPitch(pitch)
    }

    /**
     *
     * @param speechRate Sets the speech rate. 1.0 is the normal speech rate. This has no effect on any pre-recorded speech.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    fun setSpeechRate(speechRate: Float): Int {
        // The following Int values will be returned
        // ERROR(-1) SUCCESS(0)
        return tts.setSpeechRate(speechRate)
    }

    /**
     * Checks whether the TTS engine is busy speaking.
     * Note that a speech item is considered complete once it's audio data has
     * been sent to the audio mixer, or written to a file.
     *
     */
    val isSpeaking: Boolean
        get() = tts.isSpeaking

    /**
     * Interrupts the current utterance (whether played or rendered to file) and discards other utterances in the queue.
     * @return ERROR(-1) SUCCESS(0)
     */
    @ErrorOrSuccess
    fun stop(): Int = tts.stop()

    companion object {
        private const val TTS_SUCCESS = TextToSpeech.SUCCESS
        private const val TTS_ERROR = TextToSpeech.ERROR
        private const val TTS_QUEUE_ADD = TextToSpeech.QUEUE_ADD
        private const val TTS_QUEUE_FLUSH = TextToSpeech.QUEUE_FLUSH
        private const val TTS_LANG_AVAILABLE = TextToSpeech.LANG_AVAILABLE
        private const val TTS_LANG_COUNTRY_AVAILABLE = TextToSpeech.LANG_COUNTRY_AVAILABLE
        private const val TTS_LANG_COUNTRY_VAR_AVAILABLE = TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE
        private const val TTS_LANG_MISSING_DATA = TextToSpeech.LANG_MISSING_DATA
        private const val TTS_LANG_NOT_SUPPORTED = TextToSpeech.LANG_NOT_SUPPORTED
        private lateinit var tts: TextToSpeech
        private var ttsOk = false
        private val ttsParams = Bundle()
    }

    init {
        val context = appContext
        tts = TextToSpeech(context, this)
    }
}
