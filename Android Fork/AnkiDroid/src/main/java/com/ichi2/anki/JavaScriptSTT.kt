// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2023 krmanik <krmanik@outlook.com>

package com.ichi2.anki

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.ichi2.utils.Permissions.canRecordAudio

class JavaScriptSTT(
    private val context: Context,
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private var recognitionCallback: SpeechRecognitionCallback? = null
    private var language: String? = null

    interface SpeechRecognitionCallback {
        fun onResult(results: List<String>)

        fun onError(errorMessage: String)
    }

    fun setLanguage(lang: String): Boolean {
        this.language = lang
        return true
    }

    fun setRecognitionCallback(callback: SpeechRecognitionCallback) {
        this.recognitionCallback = callback
    }

    fun start(): Boolean {
        if (!canRecordAudio(context)) {
            recognitionCallback?.onError("Record audio permission not granted.")
            return false
        }

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            recognitionCallback?.onError("Speech recognition is not available on this device.")
            return false
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)

        try {
            speechRecognizer?.startListening(intent)
            return true
        } catch (e: Exception) {
            recognitionCallback?.onError("Error starting speech recognition.")
        }
        return false
    }

    fun stop(): Boolean {
        speechRecognizer?.stopListening()
        return true
    }

    private fun createRecognitionListener(): RecognitionListener =
        object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}

            override fun onBeginningOfSpeech() {}

            override fun onRmsChanged(rmsdB: Float) {}

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {}

            override fun onError(error: Int) {
                val errorMessage =
                    when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognition service busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                        else -> "Unknown error"
                    }
                recognitionCallback?.onError(errorMessage)
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                if (!matches.isNullOrEmpty()) {
                    recognitionCallback?.onResult(matches)
                } else {
                    recognitionCallback?.onError("No speech recognition results found.")
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {}

            override fun onEvent(
                eventType: Int,
                params: Bundle?,
            ) {}
        }
}
