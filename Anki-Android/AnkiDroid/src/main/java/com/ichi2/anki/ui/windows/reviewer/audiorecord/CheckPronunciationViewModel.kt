/*
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
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
package com.ichi2.anki.ui.windows.reviewer.audiorecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ichi2.anki.common.android.appContext
import com.ichi2.anki.recorder.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class CheckPronunciationViewModel(
    private val audioRecorder: AudioRecorder = AudioRecorder(appContext),
    private val audioPlayer: AudioPlayer = AudioPlayer(),
) : ViewModel() {
    init {
        addCloseable(audioPlayer)
        addCloseable(audioRecorder)

        audioPlayer.onCompletion = {
            viewModelScope.launch {
                playbackProgressFlow.emit(playbackProgressBarMaxFlow.value)
                isPlayingFlow.emit(false)
            }
        }
    }

    val playbackProgressFlow = MutableStateFlow(0)
    val playbackProgressBarMaxFlow = MutableStateFlow(1)
    val isPlayingFlow = MutableStateFlow(false)
    val replayFlow = MutableSharedFlow<Unit>()
    val isPlaybackVisibleFlow = MutableStateFlow(false)

    private var progressBarUpdateJob: Job? = null
    private val currentFile get() = audioRecorder.currentFile
    private val isPlaying get() = audioPlayer.isPlaying

    fun onRecordingStarted() {
        audioRecorder.start()
        onCancelPlayback()
    }

    fun onRecordingCancelled() {
        audioRecorder.stop()
    }

    fun onRecordingCompleted() {
        audioRecorder.stop()
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(true)
            isPlayingFlow.emit(false)
            playbackProgressFlow.emit(0)
        }
    }

    fun pausePlayback() {
        if (isPlaying) {
            progressBarUpdateJob?.cancel()
            audioPlayer.pause()
            viewModelScope.launch {
                isPlayingFlow.emit(false)
            }
        }
    }

    fun onPlayOrReplay() {
        if (!isPlaybackVisibleFlow.value) return

        if (isPlaying) {
            replayCurrentFile()
            viewModelScope.launch {
                replayFlow.emit(Unit)
            }
        } else if (audioPlayer.isPaused) {
            viewModelScope.launch { isPlayingFlow.emit(true) }
            audioPlayer.resume()
            launchProgressBarUpdateJob()
        } else {
            viewModelScope.launch { isPlayingFlow.emit(true) }
            playCurrentFile()
        }
    }

    fun onCancelPlayback() {
        progressBarUpdateJob?.cancel()
        audioPlayer.close()
        viewModelScope.launch {
            isPlaybackVisibleFlow.emit(false)
            playbackProgressFlow.emit(0)
            isPlayingFlow.emit(false)
        }
    }

    fun resetAll() {
        onRecordingCancelled()
        onCancelPlayback()
    }

    private fun playCurrentFile() {
        val filePath = currentFile?.absolutePath ?: return
        audioPlayer.play(filePath) {
            viewModelScope.launch {
                playbackProgressBarMaxFlow.emit(audioPlayer.duration)
                launchProgressBarUpdateJob()
            }
        }
    }

    private fun replayCurrentFile() {
        audioPlayer.replay()
        launchProgressBarUpdateJob()
    }

    private fun launchProgressBarUpdateJob() {
        progressBarUpdateJob?.cancel()
        progressBarUpdateJob =
            viewModelScope.launch {
                while (isPlaying) {
                    playbackProgressFlow.emit(audioPlayer.currentPosition)
                    delay(50L)
                }
            }
    }
}
