// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.EmptyApplicationCategory
import com.ichi2.anki.recorder.PlayerState.Empty
import com.ichi2.anki.recorder.PlayerState.Failed
import com.ichi2.anki.recorder.PlayerState.Loading
import com.ichi2.anki.recorder.PlayerState.Ready
import com.ichi2.testutils.EmptyApplication
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.job
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.junit.Rule
import org.junit.Test
import org.junit.experimental.categories.Category
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
@Config(application = EmptyApplication::class)
@Category(EmptyApplicationCategory::class)
class VoicePlayerTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val playerStub = PlayerStub()
    private val recording by lazy { File(tempFolder.root, "recording.3gp") }

    @Test
    fun `no player is created before the first load`() =
        runTest {
            val voicePlayer = createVoicePlayer()

            voicePlayer.play()
            voicePlayer.seekTo(1.seconds)

            assertThat("players created", playerStub.createdCount, equalTo(0))
            assertThat(voicePlayer.state.value, equalTo(Empty))
        }

    @Test
    fun `one player with one listener serves every load`() =
        runTest {
            val voicePlayer = createVoicePlayer()

            voicePlayer.load(recording)
            voicePlayer.load(recording)

            assertThat("players created", playerStub.createdCount, equalTo(1))
            assertThat("listeners", playerStub.listeners, hasSize(1))
        }

    @Test
    fun `load pauses, sets the recording and prepares`() =
        runTest {
            val voicePlayer = createVoicePlayer()

            voicePlayer.load(recording)

            assertThat(voicePlayer.state.value, equalTo(Loading))
            verifyOrder {
                playerStub.player.pause()
                playerStub.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(recording)))
                playerStub.player.prepare()
            }
        }

    @Test
    fun `the first ready reports the duration without playing`() =
        runTest {
            val voicePlayer = loadedPlayer()

            assertThat(voicePlayer.state.value, equalTo(Ready(3.seconds, isPlaying = false)))
        }

    @Test
    fun `an unknown duration reads as zero`() =
        runTest {
            playerStub.duration = null

            val voicePlayer = loadedPlayer()

            assertThat(voicePlayer.state.value, equalTo(Ready(Duration.ZERO, isPlaying = false)))
        }

    @Test
    fun `playing while loading starts once the recording is ready`() =
        runTest {
            val voicePlayer = createVoicePlayer()
            voicePlayer.load(recording)

            voicePlayer.play()
            assertThat("still loading", voicePlayer.state.value, equalTo(Loading))
            playerStub.ready()
            playerStub.currentPosition = 1.seconds
            runCurrent()

            assertThat(voicePlayer.state.value, equalTo(Ready(3.seconds, isPlaying = true)))
            assertThat("position follows the player", voicePlayer.position.value, equalTo(1.seconds))
        }

    @Test
    fun `playing is shown only once the player reports it`() =
        runTest {
            playerStub.reportsPlayWhenReady = false
            val voicePlayer = loadedPlayer()

            voicePlayer.play()

            assertThat("is playing", voicePlayer.isPlaying, equalTo(false))
        }

    @Test
    fun `losing audio focus shows playback as paused`() =
        runTest {
            val voicePlayer = loadedPlayer()
            voicePlayer.play()

            playerStub.focusLost()

            assertThat("is playing", voicePlayer.isPlaying, equalTo(false))
        }

    @Test
    fun `the position only ticks while playing`() =
        runTest {
            val voicePlayer = loadedPlayer()
            advanceBy(1.seconds)
            assertThat("reads while paused", playerStub.positionReads, equalTo(0))

            voicePlayer.play()
            playerStub.currentPosition = 500.milliseconds
            advanceBy(160.milliseconds)
            val readsWhilePlaying = playerStub.positionReads
            assertThat("reads while playing", readsWhilePlaying, greaterThan(1))
            assertThat(voicePlayer.position.value, equalTo(500.milliseconds))

            voicePlayer.pause()
            advanceBy(1.seconds)
            assertThat("reads after pausing", playerStub.positionReads, equalTo(readsWhilePlaying))
        }

    @Test
    fun `a ready after a seek keeps playing and the new position`() =
        runTest {
            val voicePlayer = loadedPlayer()
            voicePlayer.play()

            voicePlayer.seekTo(2.seconds)
            playerStub.duration = 4.seconds
            playerStub.ready()

            assertThat(voicePlayer.state.value, equalTo(Ready(4.seconds, isPlaying = true)))
            assertThat(voicePlayer.position.value, equalTo(2.seconds))
        }

    @Test
    fun `seekTo clamps to the recording and publishes at once`() =
        runTest {
            val voicePlayer = loadedPlayer()

            voicePlayer.seekTo(5.seconds)
            assertThat("past the end", voicePlayer.position.value, equalTo(3.seconds))
            voicePlayer.seekTo((-1).seconds)
            assertThat("before the start", voicePlayer.position.value, equalTo(Duration.ZERO))

            verifyOrder {
                playerStub.player.seekTo(3000L)
                playerStub.player.seekTo(0L)
            }
        }

    @Test
    fun `seekBy starts from the live position and clamps`() =
        runTest {
            val voicePlayer = loadedPlayer()

            playerStub.currentPosition = 2.seconds
            voicePlayer.seekBy(5.seconds)
            assertThat("forward past the end", voicePlayer.position.value, equalTo(3.seconds))

            playerStub.currentPosition = 1.seconds
            voicePlayer.seekBy((-5).seconds)
            assertThat("back past the start", voicePlayer.position.value, equalTo(Duration.ZERO))
        }

    @Test
    fun `the end pauses and keeps the position at the end`() =
        runTest {
            val voicePlayer = loadedPlayer()
            voicePlayer.play()

            playerStub.ended()

            assertThat(voicePlayer.state.value, equalTo(Ready(3.seconds, isPlaying = false)))
            assertThat(voicePlayer.position.value, equalTo(3.seconds))
            verifyOrder {
                playerStub.player.play()
                playerStub.player.pause()
            }
        }

    @Test
    fun `playing after the end starts from the beginning`() =
        runTest {
            val voicePlayer = loadedPlayer()
            voicePlayer.play()
            playerStub.ended()

            voicePlayer.play()

            assertThat(voicePlayer.position.value, equalTo(Duration.ZERO))
            assertThat("is playing", voicePlayer.isPlaying, equalTo(true))
            verifyOrder {
                playerStub.player.seekTo(0L)
                playerStub.player.play()
            }
        }

    @Test
    fun `an error fails playback and a new load recovers`() =
        runTest {
            val voicePlayer = loadedPlayer()
            voicePlayer.play()
            advanceBy(100.milliseconds)

            playerStub.fail()
            val readsAtError = playerStub.positionReads
            assertThat("reads while playing", readsAtError, greaterThan(0))
            advanceBy(1.seconds)

            assertThat(voicePlayer.state.value, equalTo(Failed))
            assertThat("reads after the error", playerStub.positionReads, equalTo(readsAtError))

            voicePlayer.load(recording)
            playerStub.ready()
            assertThat(voicePlayer.state.value, equalTo(Ready(3.seconds, isPlaying = false)))
        }

    @Test
    fun `events fired while unloading are ignored`() =
        runTest {
            val voicePlayer = loadedPlayer()
            playerStub.onStop = {
                playerStub.ready()
                playerStub.fail()
            }

            voicePlayer.unload()

            assertThat(voicePlayer.state.value, equalTo(Empty))
            assertThat(voicePlayer.position.value, equalTo(Duration.ZERO))
            verifyOrder {
                playerStub.player.stop()
                playerStub.player.clearMediaItems()
            }
        }

    @Test
    fun `close releases the player once and stops the ticker`() =
        runTest {
            val voicePlayer = loadedPlayer()
            voicePlayer.play()
            advanceBy(100.milliseconds)

            voicePlayer.close()
            voicePlayer.close()
            voicePlayer.load(recording)
            voicePlayer.play()
            val readsAtClose = playerStub.positionReads
            advanceBy(1.seconds)

            verify(exactly = 1) { playerStub.player.release() }
            assertThat("players created", playerStub.createdCount, equalTo(1))
            assertThat(voicePlayer.state.value, equalTo(Empty))
            assertThat("reads after close", playerStub.positionReads, equalTo(readsAtClose))
            val scopeJobs = backgroundScope.coroutineContext.job.children
            assertThat("ticker running", scopeJobs.any { it.isActive }, equalTo(false))
        }

    private fun TestScope.createVoicePlayer() = VoicePlayer(backgroundScope, playerStub.factory)

    private fun TestScope.loadedPlayer() =
        createVoicePlayer().apply {
            load(recording)
            playerStub.ready()
        }

    private fun TestScope.advanceBy(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private val VoicePlayer.isPlaying: Boolean
        get() = (state.value as? Ready)?.isPlaying == true
}
