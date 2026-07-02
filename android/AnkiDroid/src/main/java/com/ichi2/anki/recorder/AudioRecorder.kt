/*
 * Copyright (c) 2013 Zaur Molotnikov <qutorial@gmail.com>
 * Copyright (c) 2013 Nicolas Raoul <nicolas.raoul@gmail.com>
 * Copyright (c) 2013 Flavio Lerda <flerda@gmail.com>
 * Copyright (c) 2021 David Allison <davidallisongithub@gmail.com>
 * Copyright (c) 2025 Brayan Oliveira <69634269+brayandso@users.noreply.github.com>
 * Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.ichi2.anki.recorder

import android.content.Context
import android.media.MediaRecorder
import com.ichi2.anki.compat.CompatHelper
import timber.log.Timber
import java.io.Closeable
import java.io.File

/**
 * A robust wrapper for [MediaRecorder] designed for usage in both Activities and Services.
 *
 * This class handles hardware fallbacks (AAC to AMR), state management for pausing/resuming,
 * and automatic resource cleanup via [Closeable].
 *
 * @property context The context used to initialize the MediaRecorder.
 */
class AudioRecorder(
    private val context: Context,
) : Closeable {
    private var recorder: MediaRecorder? = null
    private var isTempFile = false

    /**
     * Indicates whether the recorder is currently capturing audio.
     */
    var isRecording = false
        private set

    /**
     * The file currently being used for recording. Null if not recording.
     */
    var currentFile: File? = null
        private set

    /**
     * Starts audio capture.
     *
     * If [file] is provided, it will be used as the destination. Otherwise, a temporary
     * file is created in the app's cache directory.
     *
     * @param file The destination file for the recording.
     * @throws IllegalStateException if called while already recording.
     */
    fun start(file: File? = null) {
        Timber.i("AudioRecorder::startRecording (isRecording %b)", isRecording)
        if (isRecording) return

        isTempFile = file == null
        val target = file ?: createTempFile() ?: return
        currentFile = target

        val mediaRecorder = recorder ?: CompatHelper.compat.getMediaRecorder(context).also { recorder = it }

        // Attempt High Quality (AAC), fallback to Standard (AMR) if it fails
        if (!configure(mediaRecorder, target, useHighQuality = true)) {
            Timber.i("HQ configuration failed, falling back to AMR_NB")
            mediaRecorder.reset()
            configure(mediaRecorder, target, useHighQuality = false)
        }

        try {
            mediaRecorder.start()
            isRecording = true
        } catch (e: IllegalStateException) {
            Timber.w(e, "MediaRecorder started in wrong state")
        } catch (e: Exception) {
            Timber.w(e, "MediaRecorder start failed")
        }
    }

    /**
     * Configures the [MediaRecorder] parameters.
     *
     * @param mediaRecorder The instance to configure.
     * @param file The output file.
     * @param useHighQuality If true, uses AAC @ 44.1kHz. If false, uses AMR_NB.
     * @return True if configuration and [MediaRecorder.prepare] succeeded.
     */
    private fun configure(
        mediaRecorder: MediaRecorder,
        file: File,
        useHighQuality: Boolean,
    ): Boolean =
        try {
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mediaRecorder.setOutputFile(file.absolutePath)

            if (useHighQuality) {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                mediaRecorder.setAudioSamplingRate(44100)
                mediaRecorder.setAudioEncodingBitRate(192000)
                mediaRecorder.setAudioChannels(2)
            } else {
                mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            }

            mediaRecorder.prepare()
            true
        } catch (e: Exception) {
            Timber.w("Configuration failed: ${e.message}")
            false
        }

    /**
     * Stops the recording and updates state.
     * @param keepFile If false, deletes the file (only if it was a temp file).
     */
    fun stop(keepFile: Boolean = true) {
        if (!isRecording) return

        try {
            recorder?.stop()
        } catch (e: RuntimeException) {
            Timber.w(e, "Failed to stop recorder: likely called too soon after start")
            deleteCurrentFile()
        } finally {
            isRecording = false

            if (!keepFile && isTempFile) {
                deleteCurrentFile()
            }

            if (!keepFile) {
                currentFile = null
            }
            isTempFile = false
        }
    }

    /**
     * Pauses the current recording session.
     */
    fun pause() {
        if (isRecording) {
            recorder?.pause()
        }
    }

    /**
     * Resumes a previously paused recording session.
     */
    fun resume() {
        if (isRecording) {
            recorder?.resume()
        }
    }

    /**
     * Retrieves the maximum absolute amplitude sampled since the last call.
     */
    fun getMaxAmplitude(): Int = recorder?.maxAmplitude ?: 0

    /**
     * Generates a temporary file in the application's cache directory.
     * Returns null if the file cannot be created due to storage issues or
     * restricted permissions.
     */
    private fun createTempFile(): File? =
        try {
            File.createTempFile("rec_", ".3gp", context.cacheDir)
        } catch (e: Exception) {
            Timber.w(e, "Could not create temporary recording file")
            null
        }

    /**
     * Stops recording and releases the [MediaRecorder] resources.
     * Should be called in `onDestroy()` or when the class is no longer needed.
     */
    override fun close() {
        try {
            if (isRecording) {
                // If closing while recording, we assume it's an abort
                stop(keepFile = !isTempFile)
            }
        } finally {
            recorder?.release()
            recorder = null
        }
    }

    private fun deleteCurrentFile() {
        val file = currentFile ?: return
        if (file.exists() && !file.delete()) {
            Timber.w("Failed to delete temporary recording file: ${file.absolutePath}")
        } else {
            Timber.d("Deleted temporary recording file")
        }
    }
}
