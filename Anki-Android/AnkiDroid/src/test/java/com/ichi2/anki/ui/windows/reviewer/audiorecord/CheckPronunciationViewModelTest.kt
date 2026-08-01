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

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.testutils.JvmTest
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.launch
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CheckPronunciationViewModelTest : JvmTest() {
    private lateinit var mockRecorder: AudioRecorder
    private lateinit var mockPlayer: AudioPlayer
    private lateinit var viewModel: CheckPronunciationViewModel

    private val onPreparedCallback = slot<() -> Unit>()
    private var onCompletionCallback: (() -> Unit)? = null

    private var isPlayingMock = false
    private var isPausedMock = false

    @Before
    fun setup() {
        mockRecorder =
            mockk(relaxUnitFun = true) {
                every { currentFile?.absolutePath } returns "test_file.3gp"
            }
        mockPlayer =
            mockk(relaxUnitFun = true) {
                every { play("test_file.3gp", capture(onPreparedCallback)) } just runs
                every { isPlaying } answers { isPlayingMock }
                every { isPaused } answers { isPausedMock }
                every { duration } returns 3000
                every { currentPosition } returns 1500
                every { onCompletion = any() } answers {
                    onCompletionCallback = firstArg()
                }
            }

        viewModel = CheckPronunciationViewModel(mockRecorder, mockPlayer)
    }

    @Test
    fun `onRecordingCompleted should make playback visible`() =
        runTest {
            viewModel.isPlaybackVisibleFlow.test {
                assertFalse(awaitItem())
                viewModel.onRecordingCompleted()
                assertTrue(awaitItem())
            }
            verify { mockRecorder.stop() }
        }

    @Test
    fun `onPlayOrReplay when stopped should start playback and update UI`() =
        runTest {
            // Precondition: Playback view must be visible to allow play
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = false
            isPausedMock = false

            viewModel.isPlayingFlow.test {
                assertFalse(awaitItem())
                viewModel.onPlayOrReplay()
                assertTrue(awaitItem())
            }

            verify { mockPlayer.play("test_file.3gp", any()) }

            viewModel.playbackProgressBarMaxFlow.test {
                assertEquals(1, awaitItem())
                onPreparedCallback.captured.invoke()
                assertEquals(3000, awaitItem())
            }
        }

    @Test
    fun `onPlayOrReplay when playing should replay audio`() =
        runTest {
            // Precondition: Playback view must be visible
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = true

            viewModel.replayFlow.test {
                viewModel.onPlayOrReplay()
                assertEquals(Unit, awaitItem())
            }

            verify { mockPlayer.replay() }
            // Allow the progress bar job to complete
            isPlayingMock = false
        }

    @Test
    fun `onPlayOrReplay when paused should resume playback and update UI`() =
        runTest {
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = false
            isPausedMock = true

            viewModel.isPlayingFlow.test {
                assertFalse(awaitItem())
                viewModel.onPlayOrReplay()
                assertTrue(awaitItem())
            }

            verify { mockPlayer.resume() }
        }

    @Test
    fun `pausePlayback should pause audio and update UI`() =
        runTest {
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = true
            isPausedMock = false

            viewModel.isPlayingFlow.value = true
            viewModel.isPlayingFlow.test {
                assertTrue(awaitItem())
                viewModel.pausePlayback()
                assertFalse(awaitItem())
            }

            verify { mockPlayer.pause() }
        }

    @Test
    fun `when playback completes should reset playing state and fill progress`() =
        runTest {
            // Start playback to set the state
            viewModel.isPlaybackVisibleFlow.value = true
            isPlayingMock = false
            viewModel.onPlayOrReplay()
            onPreparedCallback.captured.invoke()
            isPlayingMock = true

            // Launch collectors concurrently
            val stateJob =
                launch {
                    viewModel.isPlayingFlow.test {
                        assertTrue(awaitItem())
                        assertFalse(awaitItem())
                    }
                }
            val progressJob =
                launch {
                    viewModel.playbackProgressFlow.test {
                        awaitItem() // initial 0
                        assertEquals(1500, awaitItem()) // from progress job
                        assertEquals(3000, awaitItem()) // from completion
                    }
                }

            // Trigger the completion event
            onCompletionCallback?.invoke()

            // Clean up
            stateJob.cancel()
            progressJob.cancel()
        }

    @Test
    fun `onRecordingStarted should hide playback and start recording`() =
        runTest {
            // Make playback visible first to test that it gets hidden
            viewModel.onRecordingCompleted()

            viewModel.isPlaybackVisibleFlow.test {
                assertTrue(awaitItem())
                viewModel.onRecordingStarted()
                assertFalse(awaitItem())
            }

            verify { mockRecorder.start() }
            coVerify { mockPlayer.close() }
        }

    @Test
    fun `onRecordingCancelled should reset recorder`() {
        viewModel.onRecordingCancelled()
        verify { mockRecorder.stop() }
    }

    @Test
    fun `resetAll should reset recorder and player`() {
        viewModel.resetAll()
        verify { mockRecorder.stop() }
        verify { mockPlayer.close() }
    }
}
