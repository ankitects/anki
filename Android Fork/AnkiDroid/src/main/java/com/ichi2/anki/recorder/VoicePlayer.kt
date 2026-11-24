// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import android.content.Context
import android.net.Uri
import androidx.annotation.MainThread
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.Closeable
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

sealed interface PlayerState {
    data object Empty : PlayerState

    data object Loading : PlayerState

    data class Ready(
        val duration: Duration,
        val isPlaying: Boolean,
    ) : PlayerState

    data object Failed : PlayerState
}

@MainThread
class VoicePlayer(
    private val scope: CoroutineScope,
    private val createPlayer: () -> Player,
) : Closeable {
    val state: StateFlow<PlayerState>
        field = MutableStateFlow<PlayerState>(PlayerState.Empty)

    val position: StateFlow<Duration>
        field = MutableStateFlow(Duration.ZERO)

    private var player: Player? = null
    private var ticker: Job? = null
    private var released = false

    private val listener =
        object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> onReady()
                    Player.STATE_ENDED -> onEnded()
                }
            }

            override fun onPlayWhenReadyChanged(
                playWhenReady: Boolean,
                reason: Int,
            ) {
                val ready = state.value as? PlayerState.Ready ?: return
                setState(ready.copy(isPlaying = playWhenReady))
            }

            override fun onPlayerError(error: PlaybackException) {
                if (state.value == PlayerState.Empty) return
                Timber.w(error, "Could not play the recording")
                setState(PlayerState.Failed)
            }
        }

    fun load(file: File) {
        if (released) return
        val player = obtainPlayer()
        setState(PlayerState.Loading)
        position.value = Duration.ZERO
        player.pause()
        player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
        player.prepare()
    }

    fun play() {
        val player = activePlayer() ?: return
        if (player.playbackState == Player.STATE_ENDED) {
            seekTo(Duration.ZERO)
        }
        player.play()
    }

    fun pause() {
        activePlayer()?.pause()
    }

    fun seekTo(position: Duration) {
        val ready = state.value as? PlayerState.Ready ?: return
        val player = player ?: return
        val target = position.coerceIn(Duration.ZERO, ready.duration)
        player.seekTo(target.inWholeMilliseconds)
        this.position.value = target
    }

    fun seekBy(offset: Duration) {
        if (state.value !is PlayerState.Ready) return
        val player = player ?: return
        seekTo(player.currentPosition.milliseconds + offset)
    }

    fun unload() {
        setState(PlayerState.Empty)
        position.value = Duration.ZERO
        player?.run {
            stop()
            clearMediaItems()
        }
    }

    override fun close() {
        if (released) return
        released = true
        setState(PlayerState.Empty)
        player?.release()
        player = null
    }

    private fun obtainPlayer(): Player =
        player ?: createPlayer().also {
            it.addListener(listener)
            player = it
        }

    private fun activePlayer(): Player? {
        val state = state.value
        if (state != PlayerState.Loading && state !is PlayerState.Ready) return null
        return player
    }

    private fun onReady() {
        val player = player ?: return
        val duration = if (player.duration == C.TIME_UNSET) Duration.ZERO else player.duration.milliseconds
        when (val current = state.value) {
            PlayerState.Loading -> setState(PlayerState.Ready(duration, player.playWhenReady))
            is PlayerState.Ready -> setState(current.copy(duration = duration))
            PlayerState.Empty, PlayerState.Failed -> {}
        }
    }

    private fun onEnded() {
        val ready = state.value as? PlayerState.Ready ?: return
        player?.pause()
        position.value = ready.duration
    }

    private fun setState(value: PlayerState) {
        state.value = value
        if (value is PlayerState.Ready && value.isPlaying) {
            if (ticker?.isActive == true) return
            ticker =
                scope.launch {
                    while (isActive) {
                        player?.let { position.value = it.currentPosition.milliseconds }
                        delay(TICK_INTERVAL)
                    }
                }
        } else {
            ticker?.cancel()
            ticker = null
        }
    }

    companion object {
        private val TICK_INTERVAL = 16.milliseconds

        fun createExoPlayer(context: Context): Player =
            ExoPlayer
                .Builder(context.applicationContext)
                .setAudioAttributes(
                    AudioAttributes
                        .Builder()
                        .setUsage(C.USAGE_MEDIA)
                        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
                        .build(),
                    true,
                ).setHandleAudioBecomingNoisy(true)
                .build()
    }
}
