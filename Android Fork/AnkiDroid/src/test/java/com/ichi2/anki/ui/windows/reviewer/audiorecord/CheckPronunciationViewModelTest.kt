// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.MediaItem
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.anki.recorder.PlayerStub
import com.ichi2.anki.recorder.VoiceRecorder
import com.ichi2.testutils.rules.MainDispatcherRule
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(AndroidJUnit4::class)
class CheckPronunciationViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @get:Rule
    val recordingsFolder = TemporaryFolder()

    private val mediaRecorderStub = MediaRecorderStub()
    private val playerStub = PlayerStub()
    private val viewModelStore = ViewModelStore()

    @Before
    fun installMediaRecorder() {
        mediaRecorderStub.install()
    }

    @After
    fun uninstallMediaRecorder() {
        mediaRecorderStub.uninstall()
    }

    @Test
    fun `onRecordingCompleted should make playback visible`() =
        runViewModelTest { viewModel ->
            viewModel.isPlaybackVisibleFlow.test {
                assertFalse(awaitItem())
                viewModel.record()
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `no player is created until the first play`() =
        runViewModelTest { viewModel ->
            viewModel.record()

            assertEquals(0, playerStub.createdCount, "players created")
        }

    @Test
    fun `onPlayOrReplay when stopped should start playback and update UI`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModel.onPlayOrReplay()
            playerStub.ready()

            verify { playerStub.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(recording))) }
            assertTrue(viewModel.isPlayingFlow.first(), "playing")
            assertEquals(3000, viewModel.playbackProgressBarMaxFlow.first(), "progress bar max")
        }

    @Test
    fun `the play button shows playing only once the recording has loaded`() =
        runViewModelTest { viewModel ->
            viewModel.record()

            viewModel.onPlayOrReplay()

            assertFalse(viewModel.isPlayingFlow.first(), "not playing while the recording loads")
            playerStub.ready()
            assertTrue(viewModel.isPlayingFlow.first(), "playing once loaded")
        }

    @Test
    fun `pressing play again while the recording loads does not reload it`() =
        runViewModelTest { viewModel ->
            viewModel.record()

            viewModel.onPlayOrReplay()
            viewModel.onPlayOrReplay()

            verify(exactly = 1) { playerStub.player.setMediaItem(any()) }
            verify(exactly = 1) { playerStub.player.prepare() }
        }

    @Test
    fun `onPlayOrReplay when playing should replay audio`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()

            viewModel.replayFlow.test {
                viewModel.onPlayOrReplay()
                assertEquals(Unit, awaitItem())
            }

            verify { playerStub.player.seekTo(0L) }
            assertEquals(0, viewModel.playbackProgressFlow.first(), "progress")
            assertTrue(viewModel.isPlayingFlow.first(), "playing")
        }

    @Test
    fun `onPlayOrReplay when paused should resume playback and update UI`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()
            viewModel.pausePlayback()

            viewModel.onPlayOrReplay()

            assertTrue(viewModel.isPlayingFlow.first(), "playing")
            verify(exactly = 1) { playerStub.player.setMediaItem(any()) }
        }

    @Test
    fun `pausePlayback should pause audio and update UI`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()

            viewModel.pausePlayback()

            assertFalse(viewModel.isPlayingFlow.first(), "playing")
            verify { playerStub.player.pause() }
        }

    @Test
    fun `when playback completes should reset playing state and fill progress`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()

            playerStub.ended()

            assertFalse(viewModel.isPlayingFlow.first(), "playing")
            assertEquals(3000, viewModel.playbackProgressFlow.first(), "progress")
            assertEquals(3000, viewModel.playbackProgressBarMaxFlow.first(), "progress bar max")
        }

    @Test
    fun `playing after the end starts from the beginning`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()
            playerStub.ended()

            viewModel.onPlayOrReplay()

            assertEquals(0, viewModel.playbackProgressFlow.first(), "progress")
            assertTrue(viewModel.isPlayingFlow.first(), "playing")
        }

    @Test
    fun `a recording that fails to play shows play again and can be retried`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()

            playerStub.fail()
            assertFalse(viewModel.isPlayingFlow.first(), "playing after the error")
            viewModel.onPlayOrReplay()

            verify(exactly = 2) { playerStub.player.setMediaItem(any()) }
        }

    @Test
    fun `clearing the ViewModel releases the player`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()

            viewModelStore.clear()

            verify { playerStub.player.release() }
        }

    @Test
    fun `onRecordingStarted should hide playback and start recording`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()

            viewModel.isPlaybackVisibleFlow.test {
                assertTrue(awaitItem())
                viewModel.onRecordingStarted()
                assertFalse(awaitItem())
            }

            verify(exactly = 2) { mediaRecorderStub.mediaRecorder.start() }
            verify { playerStub.player.stop() }
        }

    @Test
    fun `a cancelled recording leaves no file`() =
        runViewModelTest { viewModel ->
            viewModel.onRecordingStarted()

            viewModel.onRecordingCancelled()

            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the cancelled recording is deleted")
        }

    @Test
    fun `recording again deletes the previous recording`() =
        runViewModelTest { viewModel ->
            val previous = viewModel.record()

            val current = viewModel.record()

            assertFalse(previous.exists(), "the previous recording is deleted")
            assertTrue(current.exists(), "the new recording is kept")
        }

    @Test
    fun `starting again while recording replaces the recording`() =
        runViewModelTest { viewModel ->
            viewModel.onRecordingStarted()
            val previous = mediaRecorderStub.outputFiles.single()

            val current = viewModel.record()

            assertFalse(previous.exists(), "the interrupted recording is deleted")
            assertTrue(current.exists(), "the new recording is kept")
        }

    @Test
    fun `cancelling playback deletes the recording`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModel.onCancelPlayback()

            assertFalse(recording.exists(), "the dismissed recording is deleted")
        }

    @Test
    fun `cancelling playback keeps a recording in progress`() =
        runViewModelTest { viewModel ->
            viewModel.onRecordingStarted()

            viewModel.onCancelPlayback()
            viewModel.onRecordingCompleted()

            assertTrue(viewModel.isPlaybackVisibleFlow.value, "the recording can still be played")
            assertTrue(mediaRecorderStub.outputFiles.single().exists(), "the recording is kept")
        }

    @Test
    fun `resetAll deletes the recording and unloads the player`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.recordAndPlay()

            viewModel.resetAll()

            assertFalse(recording.exists(), "the recording is deleted")
            assertFalse(viewModel.isPlaybackVisibleFlow.value, "playback is hidden")
            verify { playerStub.player.stop() }
        }

    @Test
    fun `clearing the ViewModel deletes the recording`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModelStore.clear()

            assertFalse(recording.exists(), "the recording is deleted when the study screen closes")
        }

    @Test
    fun `a recording that fails to start shows no playback and leaves no file`() =
        runViewModelTest { viewModel ->
            mediaRecorderStub.failStart = true

            viewModel.record()

            assertFalse(viewModel.isPlaybackVisibleFlow.value, "no playback for a failed recording")
            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the empty file is deleted")
        }

    @Test
    fun `recordings are stored outside the cache`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val viewModel =
                CheckPronunciationViewModel(AudioRecorder(context), playerStub.factory)
                    .also { viewModelStore.put(VIEW_MODEL_KEY, it) }
            try {
                val recording = viewModel.record()

                assertEquals(File(context.noBackupFilesDir, VoiceRecorder.RECORDINGS_DIRECTORY), recording.parentFile)
            } finally {
                viewModelStore.clear()
            }
        }

    private fun runViewModelTest(testBody: suspend TestScope.(CheckPronunciationViewModel) -> Unit) =
        runTest {
            val viewModel =
                CheckPronunciationViewModel(
                    AudioRecorder(ApplicationProvider.getApplicationContext()),
                    playerStub.factory,
                    recordingsFolder.root,
                ).also { viewModelStore.put(VIEW_MODEL_KEY, it) }
            try {
                testBody(viewModel)
            } finally {
                viewModelStore.clear()
            }
        }

    private fun CheckPronunciationViewModel.record(): File {
        onRecordingStarted()
        onRecordingCompleted()
        return mediaRecorderStub.outputFiles.last()
    }

    private fun CheckPronunciationViewModel.recordAndPlay(): File =
        record().also {
            onPlayOrReplay()
            playerStub.ready()
        }

    companion object {
        private const val VIEW_MODEL_KEY = "checkPronunciation"
    }
}
