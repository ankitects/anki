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

import android.media.MediaPlayer
import timber.log.Timber
import java.io.Closeable
import java.io.IOException

class AudioPlayer : Closeable {
    private val mediaPlayer = MediaPlayer()

    var isPlaying = false
        private set
    var isPaused = false
        private set
    var isPrepared = false

    var onCompletion: (() -> Unit)? = null

    val duration: Int
        get() = if (isPrepared) mediaPlayer.duration else 0
    val currentPosition: Int
        get() = if (isPrepared) mediaPlayer.currentPosition else 0

    init {
        mediaPlayer.setOnCompletionListener {
            isPlaying = false
            isPaused = false
            onCompletion?.invoke()
        }
    }

    fun play(
        filePath: String,
        onPrepared: () -> Unit,
    ) {
        Timber.i("AudioPlayer::play (isPlaying %b)", isPlaying)
        try {
            mediaPlayer.reset()
            isPrepared = false
            isPlaying = false
            isPaused = false

            mediaPlayer.setDataSource(filePath)
            mediaPlayer.setOnPreparedListener { mp ->
                isPrepared = true
                mp.start()
                isPlaying = true
                onPrepared()
            }
            mediaPlayer.prepareAsync()
        } catch (exception: IOException) {
            Timber.w(exception, "Could not play file %s", filePath)
            close()
        }
    }

    fun pause() {
        Timber.i("AudioPlayer::pause (isPlaying %b)", isPlaying)
        if (isPlaying) {
            mediaPlayer.pause()
            isPlaying = false
            isPaused = true
        }
    }

    fun resume() {
        Timber.i("AudioPlayer::resume (isPaused %b)", isPaused)
        if (isPaused) {
            mediaPlayer.start()
            isPlaying = true
            isPaused = false
        }
    }

    fun replay() {
        Timber.i("AudioPlayer::replay (isPlaying %b) (isPrepared %b)", isPlaying, isPrepared)
        if (isPrepared) {
            mediaPlayer.seekTo(0)
            mediaPlayer.start()
            isPlaying = true
            isPaused = false
        }
    }

    override fun close() {
        Timber.i("AudioPlayer::close (isPlaying %b) (isPrepared %b)", isPlaying, isPrepared)
        mediaPlayer.reset()
        isPrepared = false
        isPlaying = false
        isPaused = false
    }
}
