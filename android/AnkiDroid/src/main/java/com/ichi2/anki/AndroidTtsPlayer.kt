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

package com.ichi2.anki

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.TextToSpeech.ERROR
import androidx.annotation.CheckResult
import com.ichi2.anki.compat.UtteranceProgressListenerCompat
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.libanki.TtsPlayer.TtsCompletionStatus
import com.ichi2.anki.libanki.TtsVoice
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

class AndroidTtsPlayer(
    private val voices: List<TtsVoice>,
) : TtsPlayer() {
    private lateinit var scope: CoroutineScope

    // this can be null in the case that TTS failed to load
    private var tts: TextToSpeech? = null

    /** Flyweight pattern for an empty bundle */
    private val bundleFlyweight = Bundle()

    private val ttsCompletedChannel: Channel<TtsCompletionStatus> = Channel()

    private val cancelledUtterances = HashSet<String>()
    private var currentUtterance: String? = null

    suspend fun init(scope: CoroutineScope) {
        this.scope = scope
        this.tts =
            TtsVoices.createTts()?.apply {
                setOnUtteranceProgressListener(
                    object : UtteranceProgressListenerCompat() {
                        override fun onStart(utteranceId: String?) {
                            // handle calling .stopPlaying() BEFORE onStart() is called
                            if (cancelledUtterances.remove(utteranceId) && currentUtterance == utteranceId) {
                                Timber.d("immediately stopped playing %s", utteranceId)
                                stopPlaying()
                            }
                        }

                        override fun onDone(utteranceId: String?) {
                            scope.launch(Dispatchers.IO) { ttsCompletedChannel.send(TtsCompletionStatus.success()) }
                        }

                        override fun onStop(
                            utteranceId: String?,
                            interrupted: Boolean,
                        ) {
                            scope.launch(Dispatchers.IO) { ttsCompletedChannel.send(TtsCompletionStatus.stopped()) }
                        }

                        override fun onError(
                            utteranceId: String?,
                            errorCode: Int,
                        ) {
                            val error = AndroidTtsError.fromErrorCode(errorCode)
                            scope.launch(Dispatchers.IO) { ttsCompletedChannel.send(TtsCompletionStatus.failure(error)) }
                        }
                    },
                )
            }
    }

    override fun getAvailableVoices(): List<TtsVoice> = this.voices

    override suspend fun play(tag: TTSTag): TtsCompletionStatus {
        val match = voiceForTag(tag)
        if (match == null) {
            Timber.w("could not find voice for %s", tag)
            return TtsCompletionStatus.failure(AndroidTtsError.MissingVoiceError(tag))
        }

        val voice = match.voice
        if (voice !is AndroidTtsVoice) {
            Timber.w("Invalid voice for %s", tag)
            return TtsCompletionStatus.failure(AndroidTtsError.InvalidVoiceError)
        }

        return play(tag, voice).also { result ->
            Timber.d("TTS result %s", result)
        }
    }

    private suspend fun play(
        tag: TTSTag,
        voice: AndroidTtsVoice,
    ): TtsCompletionStatus =
        suspendCancellableCoroutine { continuation ->
            val tts =
                tts?.also {
                    it.voice = voice.voice
                    tag.speed?.let { speed ->
                        if (it.setSpeechRate(speed) == ERROR) {
                            return@suspendCancellableCoroutine continuation.resume(AndroidTtsError.SpeechRateFailed)
                        }
                    }
                    // if it's already playing: stop it
                    it.stopPlaying()
                } ?: return@suspendCancellableCoroutine continuation.resume(AndroidTtsError.InitFailed)

            Timber.d("tts text '%s' to be played for locale (%s)", tag.fieldText, tag.lang)
            continuation.ensureActive()
            val utteranceId =
                tag.fieldText.hashCode().toString().apply {
                    currentUtterance = this
                    cancelledUtterances.remove(this)
                }
            tts.speak(tag.fieldText, TextToSpeech.QUEUE_FLUSH, bundleFlyweight, utteranceId)

            continuation.invokeOnCancellation {
                Timber.d("stopping tts due to cancellation")
                // sadly: .stopPlaying does NOT work if the TTS Engine has queued the text
                cancelledUtterances.add(utteranceId)
                tts.stopPlaying()
            }

            scope.launch(Dispatchers.IO) {
                Timber.v("awaiting tts completion")
                continuation.resume(
                    ttsCompletedChannel.receive().also {
                        Timber.v("tts completed")
                    },
                )
            }
        }

    companion object {
        private fun TextToSpeech.stopPlaying() {
            if (this.isSpeaking) {
                Timber.d("tts engine appears to be busy... clearing queue")
                this.stop()
            }
        }

        @CheckResult
        suspend fun createInstance(scope: CoroutineScope): AndroidTtsPlayer {
            val voices = TtsVoices.allTtsVoices().toList()
            return AndroidTtsPlayer(voices).apply {
                init(scope)
                Timber.v("TTS creation: initialized player instance")
            }
        }
    }

    override fun close() {
        Timber.d("Disposing of TTS Engine")
        tts?.stop()
        tts?.shutdown()
    }
}

sealed class AndroidTtsError : TtsPlayer.TtsError() {
    // Ankidroid specific errors
    data object UnknownError : AndroidTtsError()

    data class MissingVoiceError(
        val tag: TTSTag,
    ) : AndroidTtsError()

    data object InvalidVoiceError : AndroidTtsError()

    data object SpeechRateFailed : AndroidTtsError()

    data object InitFailed : AndroidTtsError()

    data object InitTimeout : AndroidTtsError()

    // Android Errors

    /** @see TextToSpeech.ERROR */
    private data object AndroidGenericError : AndroidTtsError()

    /** @see TextToSpeech.ERROR_SYNTHESIS */
    private data object AndroidSynthesisError : AndroidTtsError()

    /** @see TextToSpeech.ERROR_INVALID_REQUEST */
    private data object AndroidInvalidRequest : AndroidTtsError()

    /** @see TextToSpeech.ERROR_NETWORK */
    private data object AndroidNetworkError : AndroidTtsError()

    /** @see TextToSpeech.ERROR_NETWORK_TIMEOUT */
    private data object AndroidNetworkTimeoutError : AndroidTtsError()

    /** @see TextToSpeech.ERROR_NOT_INSTALLED_YET */
    private data object AndroidNotInstalledYet : AndroidTtsError()

    /** @see TextToSpeech.ERROR_OUTPUT */
    private data object AndroidOutputError : AndroidTtsError()

    /** @see TextToSpeech.ERROR_SERVICE */
    private data object AndroidServiceError : AndroidTtsError()

    /** A string which google will relate to the TTS Engine in most cases */
    val developerString: String
        get() =
            when (this) {
                is AndroidGenericError -> "ERROR"
                is AndroidSynthesisError -> "ERROR_SYNTHESIS"
                is AndroidInvalidRequest -> "ERROR_INVALID_REQUEST"
                is AndroidNetworkError -> "ERROR_NETWORK_ERROR"
                is AndroidNetworkTimeoutError -> "ERROR_NETWORK_TIMEOUT"
                is AndroidNotInstalledYet -> "ERROR_NOT_INSTALLED_YET"
                is AndroidOutputError -> "ERROR_OUTPUT"
                is AndroidServiceError -> "ERROR_SERVICE"
                is MissingVoiceError -> "APP_MISSING_VOICE"
                is InvalidVoiceError -> "APP_INVALID_VOICE"
                is SpeechRateFailed -> "APP_SPEECH_RATE_FAILED"
                is InitFailed -> "APP_TTS_INIT_FAILED"
                is InitTimeout -> "APP_TTS_INIT_TIMEOUT"
                is UnknownError -> "APP_UNKNOWN"
            }

    companion object {
        fun fromErrorCode(errorCode: Int): AndroidTtsError =
            when (errorCode) {
                ERROR -> AndroidGenericError
                TextToSpeech.ERROR_SYNTHESIS -> AndroidSynthesisError
                TextToSpeech.ERROR_INVALID_REQUEST -> AndroidInvalidRequest
                TextToSpeech.ERROR_NETWORK -> AndroidNetworkError
                TextToSpeech.ERROR_OUTPUT -> AndroidOutputError
                TextToSpeech.ERROR_NOT_INSTALLED_YET -> AndroidNotInstalledYet
                TextToSpeech.ERROR_SERVICE -> AndroidServiceError
                else -> UnknownError
            }
    }
}

fun TtsPlayer.TtsError.localizedErrorMessage(context: Context): String =
    if (this is AndroidTtsError) {
        // TODO: Do we want a human readable string here as well - snackbar has limited room
        // but developerString is currently not translated as it returns
        // developerString: ERROR_NETWORK_TIMEOUT, so "Audio error (ERROR_NETWORK_TIMEOUT)"
        context.getString(R.string.tts_voices_playback_error_new, this.developerString)
    } else {
        this.toString()
    }

private fun CancellableContinuation<TtsCompletionStatus>.resume(error: AndroidTtsError) {
    resume(TtsCompletionStatus.failure(error))
}
