// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.anki.recorder.PlayerState
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.recorder.VoicePlayer
import com.ichi2.anki.recorder.VoiceRecorder
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration

class CheckPronunciationViewModel(
    audioRecorder: AudioRecorder = AudioRecorder(appContext),
    createPlayer: () -> Player = { VoicePlayer.createExoPlayer(appContext) },
    recordingsDirectory: File = VoiceRecorder.recordingsDirectory(appContext),
) : ViewModel() {
    private val recorder = VoiceRecorder(audioRecorder, recordingsDirectory, viewModelScope)
    private val player = VoicePlayer(viewModelScope, createPlayer)

    init {
        addCloseable(player)
        addCloseable(recorder)
    }

    val playbackProgressFlow: Flow<Int> = player.position.map { it.inWholeMilliseconds.toInt() }
    val playbackProgressBarMaxFlow: Flow<Int> =
        player.state
            .map { state ->
                val duration = (state as? PlayerState.Ready)?.duration ?: Duration.ZERO
                duration.inWholeMilliseconds.toInt().coerceAtLeast(1)
            }.distinctUntilChanged()
    val isPlayingFlow: Flow<Boolean> =
        player.state
            .map { state -> state is PlayerState.Ready && state.isPlaying }
            .distinctUntilChanged()
    val replayFlow = MutableSharedFlow<Unit>()
    val isPlaybackVisibleFlow = MutableStateFlow(false)

    private val currentFile get() = (recorder.state.value as? RecorderState.Recorded)?.file

    fun onRecordingStarted() {
        onCancelPlayback()
        recorder.start()
    }

    fun onRecordingCancelled() {
        recorder.discard()
    }

    fun onRecordingCompleted() {
        if (!recorder.stop()) return
        isPlaybackVisibleFlow.value = true
    }

    fun pausePlayback() {
        player.pause()
    }

    fun onPlayOrReplay() {
        if (!isPlaybackVisibleFlow.value) return
        val file = currentFile ?: return
        val state = player.state.value
        when {
            state == PlayerState.Empty || state == PlayerState.Failed -> {
                player.load(file)
                player.play()
            }
            state is PlayerState.Ready && state.isPlaying -> {
                player.seekTo(Duration.ZERO)
                viewModelScope.launch { replayFlow.emit(Unit) }
            }
            else -> player.play()
        }
    }

    fun onCancelPlayback() {
        player.unload()
        if (recorder.state.value is RecorderState.Recorded) {
            recorder.discard()
        }
        isPlaybackVisibleFlow.value = false
    }

    fun resetAll() {
        onRecordingCancelled()
        onCancelPlayback()
    }
}
