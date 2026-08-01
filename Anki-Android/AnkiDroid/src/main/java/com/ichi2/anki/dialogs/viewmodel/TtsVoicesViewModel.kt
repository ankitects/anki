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

package com.ichi2.anki.dialogs.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.AndroidTtsPlayer
import com.ichi2.anki.AndroidTtsVoice
import com.ichi2.anki.TtsVoices
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.dialogs.tryDisplayLocalizedName
import com.ichi2.anki.libanki.TTSTag
import com.ichi2.anki.libanki.TtsPlayer
import com.ichi2.anki.libanki.TtsVoice
import com.ichi2.utils.copyToClipboard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * @see [com.ichi2.anki.dialogs.TtsVoicesDialogFragment]
 */
class TtsVoicesViewModel : ViewModel() {
    /**
     * Whether we should show internet-enabled voices in the list.
     *
     * Internet-enabled voices are more 'risky' for users so we want to discourage them
     * * Costs could be incurred if the user is on a metered connection
     * * Additional latency before speech synthesis begins (typically)
     */
    val showInternetEnabled: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** Whether we filter the list to only 'not installed' voices, or show installed + other filters */
    val showNotInstalled: MutableStateFlow<Boolean> = MutableStateFlow(false)

    /** The status of loading the list of voices to use */
    private val ttsVoiceListStatus: MutableStateFlow<LoadVoiceStatus> =
        MutableStateFlow(LoadVoiceStatus.Calculating)

    /** The status of loading the TTS Engine to play previews */
    private val ttsEngineStatus: MutableStateFlow<TtsEngineStatus> =
        MutableStateFlow(TtsEngineStatus.Uninitialized)

    /** The user-selected text which we will play via the TTS Engine */
    private var textToSpeak: String = ""

    /**
     * Lists errors which are obtained from TTS Playback
     *
     * Typically: if a network voice is requested and a user is offline
     */
    val ttsPlaybackErrorFlow: MutableSharedFlow<TtsPlayer.TtsError> =
        MutableSharedFlow()

    /** When a voice is played which has not yet been installed */
    val uninstalledVoicePlayed = MutableSharedFlow<TtsVoice>()

    /** Combines individual filters into a single object */
    private val filterFlow =
        showInternetEnabled.combine(showNotInstalled) { showInternetEnabled, showNotInstalled ->
            return@combine Filters(showInternetEnabled, showNotInstalled)
        }

    /** The collection of either a [TtsEngineStatus] with voices, error, or 'loading'  */
    val availableVoicesFlow =
        ttsEngineStatus
            .combine(ttsVoiceListStatus) { a, b ->
                if (a is TtsEngineStatus.Error) {
                    return@combine VoiceLoadingState.Failure(a.exception)
                }
                if (b is LoadVoiceStatus.Error) {
                    return@combine VoiceLoadingState.Failure(b.exception)
                }

                if (a is TtsEngineStatus.Success && b is LoadVoiceStatus.Success) {
                    return@combine VoiceLoadingState.Success(b.voices)
                }
                return@combine VoiceLoadingState.Pending
            }.combine(filterFlow) { state, filter ->
                if (state is VoiceLoadingState.Success) {
                    // showNotInstalled filters the whole list
                    val voices = state.voices.filter { filter.showNotInstalled == it.unavailable() }
                    if (filter.showNotInstalled) {
                        return@combine VoiceLoadingState.Success(voices)
                    }

                    return@combine VoiceLoadingState.Success(
                        voices.filter { filter.showInternetEnabled || !it.isNetworkConnectionRequired },
                    )
                }
                return@combine state
            }

    /** Filters which can be applied to the list of TTS Voices */
    data class Filters(
        val showInternetEnabled: Boolean,
        val showNotInstalled: Boolean,
    )

    /** The state of loading the screen. When successful, voices are provided */
    interface VoiceLoadingState {
        data class Success(
            val voices: List<AndroidTtsVoice>,
        ) : VoiceLoadingState

        data object Pending : VoiceLoadingState

        data class Failure(
            val exception: Exception,
        ) : VoiceLoadingState
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            ttsVoiceListStatus.emit(LoadVoiceStatus.Calculating)
            try {
                // sort by language display name:
                // it's more intuitive to have 'Welsh' at the bottom rather than as 'cy'
                val voices =
                    TtsVoices
                        .allTtsVoices()
                        .sortedWith(
                            compareBy<AndroidTtsVoice> { it.normalizedLocale.displayName }
                                .thenBy { it.tryDisplayLocalizedName() },
                        )

                ttsVoiceListStatus.emit(LoadVoiceStatus.Success(voices))
            } catch (e: Exception) {
                ttsVoiceListStatus.emit(LoadVoiceStatus.Error(e))
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            ttsEngineStatus.emit(TtsEngineStatus.Loading)
            val player =
                AndroidTtsPlayer.createInstance(viewModelScope)
            ttsEngineStatus.emit(TtsEngineStatus.Success(player))
        }
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                TtsVoices.refresh()
                val voices =
                    TtsVoices
                        .allTtsVoices()
                        .sortedWith(
                            compareBy<AndroidTtsVoice> { it.normalizedLocale.displayName }
                                .thenBy { it.tryDisplayLocalizedName() },
                        )

                // COULD_BE_BETTER: Handle the changes in DiffUtils in the adapter
                // and don't perform updates when no changes occur
                Timber.v("voice list refreshed")
                ttsVoiceListStatus.emit(LoadVoiceStatus.Success(voices))
                delay(2_000)
            }
        }
        addCloseable {
            val currentState = ttsEngineStatus.value
            if (currentState !is TtsEngineStatus.Success) {
                return@addCloseable
            }
            currentState.ttsPlayer.close()
        }
    }

    fun setSpokenText(text: String) {
        textToSpeak = text
    }

    fun playVoice(voice: TtsVoice) =
        viewModelScope.launch(Dispatchers.IO) {
            if (voice.unavailable()) {
                uninstalledVoicePlayed.emit(voice)
            }
            playTts(textToSpeak, voice)
        }

    fun copyToClipboard(voice: TtsVoice) {
        // At least in API 33, we do not need to display a snackbar, as the Android OS already
        // displays the copied text
        appContext.copyToClipboard(
            text = voice.toString(),
        )
    }

    private suspend fun playTts(
        textToSpeak: String,
        voice: TtsVoice,
    ) {
        val currentState = ttsEngineStatus.value
        if (currentState !is TtsEngineStatus.Success) {
            return
        }
        currentState.ttsPlayer.play(textToSpeak, voice).error?.let {
            withContext(Dispatchers.Main) {
                ttsPlaybackErrorFlow.emit(it)
            }
        }
    }

    /** Waits until the next refresh of [ttsVoiceListStatus] occurs */
    fun waitForRefresh() = ttsVoiceListStatus.update { LoadVoiceStatus.Calculating }

    sealed interface LoadVoiceStatus {
        data object Calculating : LoadVoiceStatus

        class Success(
            val voices: List<AndroidTtsVoice>,
        ) : LoadVoiceStatus

        class Error(
            val exception: Exception,
        ) : LoadVoiceStatus
    }

    sealed interface TtsEngineStatus {
        data object Uninitialized : TtsEngineStatus

        data object Loading : TtsEngineStatus

        class Success(
            val ttsPlayer: TtsPlayer,
        ) : TtsEngineStatus

        class Error(
            val exception: Exception,
        ) : TtsEngineStatus
    }
}

suspend fun TtsPlayer.play(
    textToSpeak: String,
    voice: TtsVoice,
    speed: Float = 1.0f,
): TtsPlayer.TtsCompletionStatus = this.play(TTSTag(textToSpeak, voice.lang, listOf(voice.name), speed, listOf()))
