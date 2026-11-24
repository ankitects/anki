// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModelStore
import androidx.media3.common.MediaItem
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.noteeditor.AudioRecordingViewModel.Failure
import com.ichi2.anki.recorder.AudioRecorder
import com.ichi2.anki.recorder.MediaRecorderStub
import com.ichi2.anki.recorder.PlayerState
import com.ichi2.anki.recorder.PlayerStub
import com.ichi2.anki.recorder.RecorderState
import com.ichi2.anki.recorder.VoiceRecorder
import com.ichi2.testutils.rules.MainDispatcherRule
import io.mockk.every
import io.mockk.verify
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
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@RunWith(AndroidJUnit4::class)
class AudioRecordingViewModelTest {
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
    fun `recording with a pause gives one recording in the recordings folder`() =
        runViewModelTest { viewModel ->
            viewModel.startRecording()
            viewModel.pauseRecording()
            assertEquals(RecorderState.Paused, viewModel.recorderState.value, "state after pausing")
            viewModel.resumeRecording()
            viewModel.stopRecording()

            val recording = mediaRecorderStub.outputFiles.single()
            assertEquals(RecorderState.Recorded(recording), viewModel.recorderState.value, "state after stopping")
            assertEquals(recordingsFolder.root, recording.parentFile, "folder")
            assertTrue(recording.name.contains("rec"), "the name keeps 'rec' for NoteService")
            assertNull(viewModel.failure.value, "failure")
        }

    @Test
    fun `starting again keeps a recording that was not inserted`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModel.startRecording()

            assertEquals(RecorderState.Recorded(recording), viewModel.recorderState.value, "state")
            assertTrue(recording.exists(), "the recording is kept")
            verify(exactly = 1) { mediaRecorderStub.mediaRecorder.start() }
        }

    @Test
    fun `a recording that fails to start is reported and leaves no file`() =
        runViewModelTest { viewModel ->
            mediaRecorderStub.failStart = true

            viewModel.startRecording()

            assertEquals(Failure.RECORDING, viewModel.failure.value, "failure")
            assertEquals(RecorderState.Idle, viewModel.recorderState.value, "state")
            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the empty file is deleted")
            assertEquals(0, playerStub.createdCount, "players created")
        }

    @Test
    fun `a recording that fails to stop is reported and leaves no file`() =
        runViewModelTest { viewModel ->
            mediaRecorderStub.failStop = true
            viewModel.startRecording()

            viewModel.stopRecording()

            assertEquals(Failure.RECORDING, viewModel.failure.value, "failure")
            assertEquals(RecorderState.Idle, viewModel.recorderState.value, "state")
            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the failed recording is deleted")
            assertEquals(0, playerStub.createdCount, "players created")
        }

    @Test
    fun `a recording that fails to pause is reported and keeps recording`() =
        runViewModelTest { viewModel ->
            every { mediaRecorderStub.mediaRecorder.pause() } throws IllegalStateException("pause failed")
            viewModel.startRecording()

            viewModel.pauseRecording()

            assertEquals(Failure.RECORDING, viewModel.failure.value, "failure")
            assertEquals(RecorderState.Recording, viewModel.recorderState.value, "state")
        }

    @Test
    fun `commands that do not apply are ignored`() =
        runViewModelTest { viewModel ->
            viewModel.pauseRecording()
            viewModel.resumeRecording()
            viewModel.stopRecording()
            viewModel.play()
            viewModel.consumeRecording { fail("there is no recording to hand over") }

            assertNull(viewModel.failure.value, "failure")
            assertEquals(RecorderState.Idle, viewModel.recorderState.value, "state")
            assertEquals(0, playerStub.createdCount, "players created")
        }

    @Test
    fun `no player is created until the first play`() =
        runViewModelTest { viewModel ->
            viewModel.record()

            assertEquals(0, playerStub.createdCount, "players created")
        }

    @Test
    fun `play loads the recording and plays it`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()

            viewModel.play()
            playerStub.ready()

            assertEquals(1, playerStub.createdCount, "players created")
            verify { playerStub.player.setMediaItem(MediaItem.fromUri(Uri.fromFile(recording))) }
            assertEquals(PlayerState.Ready(3.seconds, isPlaying = true), viewModel.playerState.value, "player state")
        }

    @Test
    fun `play after pausing resumes without reloading`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()

            viewModel.pausePlayback()
            assertEquals(PlayerState.Ready(3.seconds, isPlaying = false), viewModel.playerState.value, "after pausing")
            viewModel.play()

            assertEquals(PlayerState.Ready(3.seconds, isPlaying = true), viewModel.playerState.value, "after resuming")
            verify(exactly = 1) { playerStub.player.setMediaItem(any()) }
        }

    @Test
    fun `skipping moves playback by 5 seconds`() =
        runViewModelTest { viewModel ->
            playerStub.duration = 20.seconds
            viewModel.recordAndPlay()
            playerStub.currentPosition = 8.seconds

            viewModel.skipForward()
            assertEquals(13.seconds, viewModel.playbackPosition.value, "after skipping forward")
            viewModel.skipBack()
            assertEquals(8.seconds, viewModel.playbackPosition.value, "after skipping back")
        }

    @Test
    fun `skipping stops at the start and the end`() =
        runViewModelTest { viewModel ->
            viewModel.recordAndPlay()
            playerStub.currentPosition = 1.seconds

            viewModel.skipBack()
            assertEquals(Duration.ZERO, viewModel.playbackPosition.value, "after skipping back")
            viewModel.skipForward()
            assertEquals(3.seconds, viewModel.playbackPosition.value, "after skipping forward")
        }

    @Test
    fun `discard unloads the recording before deleting it`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.recordAndPlay()
            var existedWhenUnloaded: Boolean? = null
            playerStub.onStop = { existedWhenUnloaded = recording.exists() }

            viewModel.discard()

            assertEquals(true, existedWhenUnloaded, "the file exists while the player unloads it")
            assertFalse(recording.exists(), "the recording is deleted")
            assertEquals(RecorderState.Idle, viewModel.recorderState.value, "recorder state")
            assertEquals(PlayerState.Empty, viewModel.playerState.value, "player state")
        }

    @Test
    fun `discard deletes a recording in progress`() =
        runViewModelTest { viewModel ->
            viewModel.startRecording()

            viewModel.discard()

            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the recording is deleted")
            assertEquals(RecorderState.Idle, viewModel.recorderState.value, "state")
        }

    @Test
    fun `consumeRecording hands the recording over once after unloading it`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.recordAndPlay()
            var unloaded = false
            playerStub.onStop = { unloaded = true }
            val handedOver = mutableListOf<File>()

            viewModel.consumeRecording {
                assertTrue(unloaded, "the player is unloaded before the handover")
                handedOver += it
            }
            viewModel.consumeRecording { handedOver += it }

            assertEquals(listOf(recording), handedOver, "recordings handed over")
            assertEquals(RecorderState.Idle, viewModel.recorderState.value, "state")
        }

    @Test
    fun `a recording that was handed over survives closing the screen`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()
            viewModel.consumeRecording { }

            viewModelStore.clear()

            assertTrue(recording.exists(), "the recording now belongs to the caller")
        }

    @Test
    fun `consumeRecording reports a recording that has gone`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.record()
            recording.delete()

            viewModel.consumeRecording { fail("a missing recording is not handed over") }

            assertEquals(Failure.RECORDING, viewModel.failure.value, "failure")
            assertEquals(RecorderState.Idle, viewModel.recorderState.value, "state")
        }

    @Test
    fun `closing the screen deletes a recording in progress`() =
        runViewModelTest { viewModel ->
            viewModel.startRecording()

            viewModelStore.clear()

            assertFalse(mediaRecorderStub.outputFiles.single().exists(), "the recording is deleted")
            verify { mediaRecorderStub.mediaRecorder.release() }
        }

    @Test
    fun `closing the screen deletes a recording that was not inserted and releases the player`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.recordAndPlay()

            viewModelStore.clear()

            assertFalse(recording.exists(), "the recording is deleted")
            verify { playerStub.player.release() }
        }

    @Test
    fun `interrupt keeps a recording in progress`() =
        runViewModelTest { viewModel ->
            viewModel.startRecording()

            viewModel.interrupt()

            val recording = mediaRecorderStub.outputFiles.single()
            assertEquals(RecorderState.Recorded(recording), viewModel.recorderState.value, "state")
            verify { mediaRecorderStub.mediaRecorder.stop() }
        }

    @Test
    fun `interrupt keeps a paused recording`() =
        runViewModelTest { viewModel ->
            viewModel.startRecording()
            viewModel.pauseRecording()

            viewModel.interrupt()

            val recording = mediaRecorderStub.outputFiles.single()
            assertEquals(RecorderState.Recorded(recording), viewModel.recorderState.value, "state")
            verify { mediaRecorderStub.mediaRecorder.stop() }
        }

    @Test
    fun `interrupt unloads the player and keeps the recording`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.recordAndPlay()

            viewModel.interrupt()

            assertEquals(PlayerState.Empty, viewModel.playerState.value, "player state")
            verify { playerStub.player.stop() }
            assertEquals(RecorderState.Recorded(recording), viewModel.recorderState.value, "recorder state")
        }

    @Test
    fun `a playback error is reported until shown and play retries`() =
        runViewModelTest { viewModel ->
            val recording = viewModel.recordAndPlay()

            playerStub.fail()
            assertEquals(Failure.PLAYBACK, viewModel.failure.value, "failure after the error")
            viewModel.failureShown()
            assertNull(viewModel.failure.value, "failure once shown")
            viewModel.play()

            verify(exactly = 2) { playerStub.player.setMediaItem(any()) }
            assertEquals(RecorderState.Recorded(recording), viewModel.recorderState.value, "the recording can still be inserted")
        }

    @Test
    fun `recordings are stored outside the cache`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val viewModel =
                AudioRecordingViewModel(AudioRecorder(context), playerStub.factory)
                    .also { viewModelStore.put(VIEW_MODEL_KEY, it) }
            try {
                val recording = viewModel.record()

                assertEquals(File(context.noBackupFilesDir, VoiceRecorder.RECORDINGS_DIRECTORY), recording.parentFile)
            } finally {
                viewModelStore.clear()
            }
        }

    private fun runViewModelTest(testBody: suspend TestScope.(AudioRecordingViewModel) -> Unit) =
        runTest {
            val viewModel =
                AudioRecordingViewModel(
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

    private fun AudioRecordingViewModel.record(): File {
        startRecording()
        stopRecording()
        return mediaRecorderStub.outputFiles.last()
    }

    private fun AudioRecordingViewModel.recordAndPlay(): File =
        record().also {
            play()
            playerStub.ready()
        }

    companion object {
        private const val VIEW_MODEL_KEY = "audioRecording"
    }
}
