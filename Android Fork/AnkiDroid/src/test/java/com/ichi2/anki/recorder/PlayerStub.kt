// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import io.mockk.every
import io.mockk.mockk
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class PlayerStub {
    val player: Player = mockk(relaxed = true)

    val factory: () -> Player = {
        createdCount++
        player
    }

    var createdCount = 0
        private set

    var positionReads = 0
        private set

    val listeners: List<Player.Listener>
        field = mutableListOf<Player.Listener>()

    var duration: Duration? = 3.seconds
    var currentPosition: Duration = Duration.ZERO
    var reportsPlayWhenReady = true
    var onStop: (() -> Unit)? = null

    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE

    init {
        every { player.addListener(any()) } answers { listeners += firstArg<Player.Listener>() }
        every { player.duration } answers { duration?.inWholeMilliseconds ?: C.TIME_UNSET }
        every { player.currentPosition } answers {
            positionReads++
            currentPosition.inWholeMilliseconds
        }
        every { player.playWhenReady } answers { playWhenReady }
        every { player.playbackState } answers { playbackState }
        every { player.play() } answers { requestPlayWhenReady(true) }
        every { player.pause() } answers { requestPlayWhenReady(false) }
        every { player.seekTo(any<Long>()) } answers {
            currentPosition = firstArg<Long>().milliseconds
            if (playbackState == Player.STATE_ENDED) playbackState = Player.STATE_READY
        }
        every { player.stop() } answers {
            playbackState = Player.STATE_IDLE
            onStop?.invoke()
        }
    }

    fun ready() = changePlaybackState(Player.STATE_READY)

    fun ended() {
        currentPosition = duration ?: Duration.ZERO
        changePlaybackState(Player.STATE_ENDED)
    }

    fun fail() {
        val error = mockk<PlaybackException>(relaxed = true)
        listeners.toList().forEach { it.onPlayerError(error) }
    }

    fun focusLost() = changePlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS)

    private fun requestPlayWhenReady(value: Boolean) {
        if (reportsPlayWhenReady) changePlayWhenReady(value, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
    }

    private fun changePlayWhenReady(
        value: Boolean,
        reason: Int,
    ) {
        if (playWhenReady == value) return
        playWhenReady = value
        listeners.toList().forEach { it.onPlayWhenReadyChanged(value, reason) }
    }

    private fun changePlaybackState(value: Int) {
        playbackState = value
        listeners.toList().forEach { it.onPlaybackStateChanged(value) }
    }
}
