// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import android.content.Context
import androidx.annotation.MainThread
import com.ichi2.anki.common.time.TimeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.Closeable
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.TimeSource

sealed interface RecorderState {
    data object Idle : RecorderState

    data object Recording : RecorderState

    data object Paused : RecorderState

    data class Recorded(
        val file: File,
    ) : RecorderState
}

@MainThread
class VoiceRecorder(
    private val audioRecorder: AudioRecorder,
    private val directory: File,
    scope: CoroutineScope,
    timeSource: TimeSource = TimeSource.Monotonic,
) : Closeable {
    val state: StateFlow<RecorderState>
        field = MutableStateFlow<RecorderState>(RecorderState.Idle)

    val elapsed: StateFlow<Duration>
        field = MutableStateFlow(Duration.ZERO)

    val amplitudes: SharedFlow<Int?>
        field = MutableSharedFlow<Int?>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val timer =
        AudioTimer(
            scope = scope,
            onTimerTick = { elapsed.value = it },
            onAudioTick = ::sampleAmplitude,
            timeSource = timeSource,
        )

    private var recordingFile: File? = null
    private var closed = false

    private val isRecordingOrPaused: Boolean
        get() = state.value == RecorderState.Recording || state.value == RecorderState.Paused

    fun start(): Boolean {
        if (closed) return false
        discard()
        val file = createRecordingFile() ?: return false
        try {
            audioRecorder.start(file)
        } catch (e: RuntimeException) {
            Timber.w(e, "Could not create the MediaRecorder")
        }
        if (!audioRecorder.isRecording) {
            file.delete()
            return false
        }
        recordingFile = file
        state.value = RecorderState.Recording
        timer.start()
        return true
    }

    fun pause(): Boolean {
        if (state.value != RecorderState.Recording) return false
        try {
            audioRecorder.pause()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Could not pause the recording")
            return false
        }
        timer.pause()
        state.value = RecorderState.Paused
        return true
    }

    fun resume(): Boolean {
        if (state.value != RecorderState.Paused) return false
        try {
            audioRecorder.resume()
        } catch (e: IllegalStateException) {
            Timber.w(e, "Could not resume the recording")
            return false
        }
        state.value = RecorderState.Recording
        timer.start()
        return true
    }

    fun stop(): Boolean {
        val file = recordingFile
        if (file == null || !isRecordingOrPaused) return false
        timer.stop()
        audioRecorder.stop()
        if (file.length() > 0) {
            state.value = RecorderState.Recorded(file)
            return true
        }
        Timber.w("The recording is missing or empty")
        deleteRecording()
        return false
    }

    fun discard() {
        if (isRecordingOrPaused) {
            timer.stop()
            audioRecorder.stop()
        }
        deleteRecording()
    }

    /**
     * Hands the finished recording to [block], which then owns the file: [start], [discard] and
     * [close] no longer delete it. Returns false if there is no finished recording.
     */
    fun consumeRecording(block: (File) -> Unit): Boolean {
        val file = (state.value as? RecorderState.Recorded)?.file ?: return false
        if (file.length() == 0L) {
            Timber.w("The recorded file is missing or empty")
            deleteRecording()
            return false
        }
        recordingFile = null
        state.value = RecorderState.Idle
        block(file)
        return true
    }

    override fun close() {
        if (closed) return
        closed = true
        discard()
        audioRecorder.close()
    }

    private fun createRecordingFile(): File? =
        try {
            directory.mkdirs()
            File.createTempFile(FILE_PREFIX, FILE_SUFFIX, directory)
        } catch (e: IOException) {
            Timber.w(e, "Could not create a recording file")
            null
        }

    private fun sampleAmplitude() {
        val amplitude =
            try {
                audioRecorder.getMaxAmplitude()
            } catch (e: IllegalStateException) {
                Timber.w(e, "Could not read the amplitude")
                null
            }
        amplitudes.tryEmit(amplitude)
    }

    private fun deleteRecording() {
        val file = recordingFile
        if (file != null && file.exists() && !file.delete()) {
            Timber.w("Could not delete %s", file)
        }
        recordingFile = null
        state.value = RecorderState.Idle
    }

    companion object {
        const val RECORDINGS_DIRECTORY = "recordings"
        private const val FILE_PREFIX = "ankidroid_audiorec"
        private const val FILE_SUFFIX = ".3gp"
        private val RECORDING_RETENTION = 7.days

        fun recordingsDirectory(context: Context): File = File(context.noBackupFilesDir, RECORDINGS_DIRECTORY)

        fun removeExpiredRecordings(
            directory: File,
            now: Long = TimeManager.time.intTimeMS(),
        ) {
            val cutoff = now - RECORDING_RETENTION.inWholeMilliseconds
            directory
                .listFiles { _, name -> name.startsWith(FILE_PREFIX) && name.endsWith(FILE_SUFFIX) }
                ?.forEach { file ->
                    val modified = file.lastModified()
                    if (file.isFile && modified > 0 && modified < cutoff && !file.delete()) {
                        Timber.w("Could not delete %s", file)
                    }
                }
        }
    }
}
