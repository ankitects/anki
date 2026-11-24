// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import android.content.Context
import com.ichi2.anki.recorder.RecorderState.Idle
import com.ichi2.anki.recorder.RecorderState.Paused
import com.ichi2.anki.recorder.RecorderState.Recorded
import com.ichi2.anki.recorder.RecorderState.Recording
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.both
import org.hamcrest.Matchers.empty
import org.hamcrest.Matchers.endsWith
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.everyItem
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.nullValue
import org.hamcrest.Matchers.startsWith
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

class VoiceRecorderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context = mockk<Context>()
    private val mediaRecorderStub = MediaRecorderStub()
    private val mediaRecorder get() = mediaRecorderStub.mediaRecorder
    private val recordingsDirectory by lazy { File(tempFolder.root, "recordings") }

    @Before
    fun installMediaRecorder() {
        mediaRecorderStub.install()
    }

    @After
    fun uninstallMediaRecorder() {
        mediaRecorderStub.uninstall()
    }

    @Test
    fun `start records into a new file`() =
        runTest {
            val recorder = createRecorder()

            assertThat("start", recorder.start(), equalTo(true))

            val files = filesOnDisk()
            assertThat("one file in $recordingsDirectory", files, hasSize(1))
            assertThat(files.single().name, both(startsWith("ankidroid_audiorec")).and(endsWith(".3gp")))
            assertThat("MediaRecorder output", mediaRecorderStub.outputFiles, equalTo(files))
            assertThat(recorder.state.value, equalTo(Recording))
        }

    @Test
    fun `start fails without a file when the microphone does not start`() =
        runTest {
            mediaRecorderStub.failStart = true

            assertStartFailsWithoutFile()
        }

    @Test
    fun `start fails without a file when the MediaRecorder cannot be created`() =
        runTest {
            mediaRecorderStub.failCreate = true

            assertStartFailsWithoutFile()
        }

    @Test
    fun `start fails when the directory cannot be created`() =
        runTest {
            val recorder = createRecorder(directory = File(tempFolder.newFile(), "recordings"))

            assertThat("start", recorder.start(), equalTo(false))

            assertThat(recorder.state.value, equalTo(Idle))
            verify(exactly = 0) { mediaRecorder.start() }
        }

    @Test
    fun `start replaces a recording in progress`() =
        runTest {
            assertStartReplacesThePreviousRecording { }
        }

    @Test
    fun `start replaces a finished recording`() =
        runTest {
            assertStartReplacesThePreviousRecording { it.stop() }
        }

    @Test
    fun `start fails after close`() =
        runTest {
            val recorder = createRecorder()
            recorder.close()

            assertThat("start", recorder.start(), equalTo(false))

            assertNoRecording(recorder)
            verify(exactly = 0) { mediaRecorder.start() }
        }

    @Test
    fun `elapsed counts recording time and freezes while paused`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()
            advanceBy(RECORDING_TIME)
            assertThat("elapsed while recording", recorder.elapsed.value, equalTo(RECORDING_TIME))

            recorder.pause()
            advanceBy(RECORDING_TIME)
            assertThat("elapsed while paused", recorder.elapsed.value, equalTo(RECORDING_TIME))

            recorder.resume()
            advanceBy(RECORDING_TIME)
            assertThat("elapsed after resume", recorder.elapsed.value, equalTo(RECORDING_TIME * 2))

            recorder.stop()
            assertThat("elapsed after stop", recorder.elapsed.value, equalTo(Duration.ZERO))
        }

    @Test
    fun `a failed pause keeps recording`() =
        runTest {
            every { mediaRecorder.pause() } throws IllegalStateException("pause failed")
            val recorder = createRecorder()
            recorder.start()
            advanceBy(RECORDING_TIME)

            assertThat("pause", recorder.pause(), equalTo(false))
            advanceBy(RECORDING_TIME)

            assertThat(recorder.state.value, equalTo(Recording))
            assertThat("elapsed keeps counting", recorder.elapsed.value, equalTo(RECORDING_TIME * 2))
        }

    @Test
    fun `a failed resume stays paused`() =
        runTest {
            every { mediaRecorder.resume() } throws IllegalStateException("resume failed")
            val recorder = createRecorder()
            recorder.start()
            advanceBy(RECORDING_TIME)
            recorder.pause()

            assertThat("resume", recorder.resume(), equalTo(false))
            advanceBy(RECORDING_TIME)

            assertThat(recorder.state.value, equalTo(Paused))
            assertThat("elapsed stays frozen", recorder.elapsed.value, equalTo(RECORDING_TIME))
        }

    @Test
    fun `amplitudes are sampled only while recording`() =
        runTest {
            every { mediaRecorder.maxAmplitude } returns AMPLITUDE
            val recorder = createRecorder()
            val samples = collectAmplitudes(recorder)
            recorder.start()
            advanceBy(RECORDING_TIME)

            val recorded = samples.toList()
            assertThat("samples while recording", recorded, not(empty()))
            assertThat(recorded, everyItem(equalTo(AMPLITUDE)))

            recorder.pause()
            advanceBy(RECORDING_TIME)
            assertThat("no samples while paused", samples, equalTo(recorded))
        }

    @Test
    fun `a failed amplitude read is sampled as null`() =
        runTest {
            every { mediaRecorder.maxAmplitude } throws IllegalStateException("no audio source")
            val recorder = createRecorder()
            val samples = collectAmplitudes(recorder)
            recorder.start()
            advanceBy(RECORDING_TIME)

            assertThat("samples while recording", samples, not(empty()))
            assertThat(samples, everyItem(nullValue()))
        }

    @Test
    fun `stop keeps the recording`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()

            assertThat("stop", recorder.stop(), equalTo(true))

            val recording = mediaRecorderStub.outputFiles.single()
            assertThat(recorder.state.value, equalTo(Recorded(recording)))
            assertThat("recording size", recording.length(), greaterThan(0L))
        }

    @Test
    fun `a failed stop deletes the file`() =
        runTest {
            mediaRecorderStub.failStop = true

            assertStopFailsWithoutFile()
        }

    @Test
    fun `an empty recording is deleted on stop`() =
        runTest {
            mediaRecorderStub.bytesOnStart = 0
            mediaRecorderStub.bytesOnStop = 0

            assertStopFailsWithoutFile()
        }

    @Test
    fun `discard while recording deletes the file`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()

            recorder.discard()

            verify { mediaRecorder.stop() }
            assertNoRecording(recorder)
        }

    @Test
    fun `discard while paused deletes the file`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()
            recorder.pause()

            recorder.discard()

            verify { mediaRecorder.stop() }
            assertNoRecording(recorder)
        }

    @Test
    fun `discard after stop deletes the file`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()
            recorder.stop()

            recorder.discard()

            assertNoRecording(recorder)
        }

    @Test
    fun `consumeRecording hands the recording over once`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()
            recorder.stop()

            val recording = assertNotNull(recorder.consumeOrNull(), "finished recording")

            assertThat(recorder.state.value, equalTo(Idle))
            assertThat("second consumeRecording", recorder.consumeOrNull(), nullValue())
            recorder.close()
            assertThat("consumed recording survives close", recording.exists(), equalTo(true))
        }

    @Test
    fun `consumeRecording has nothing to hand over until the recording is stopped`() =
        runTest {
            val recorder = createRecorder()
            assertThat("consumeRecording while idle", recorder.consumeOrNull(), nullValue())

            recorder.start()
            assertThat("consumeRecording while recording", recorder.consumeOrNull(), nullValue())
            recorder.pause()
            assertThat("consumeRecording while paused", recorder.consumeOrNull(), nullValue())

            assertThat(recorder.state.value, equalTo(Paused))
            assertThat(filesOnDisk(), hasSize(1))
        }

    @Test
    fun `consumeRecording has nothing to hand over when the file has vanished`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()
            recorder.stop()
            mediaRecorderStub.outputFiles.single().delete()

            assertThat("consumeRecording", recorder.consumeOrNull(), nullValue())

            assertThat(recorder.state.value, equalTo(Idle))
        }

    @Test
    fun `close while recording deletes the file and releases the MediaRecorder`() =
        runTest {
            val recorder = createRecorder()
            recorder.start()

            recorder.close()

            verifyOrder {
                mediaRecorder.stop()
                mediaRecorder.release()
            }
            assertNoRecording(recorder)
        }

    private fun TestScope.createRecorder(directory: File = recordingsDirectory) =
        VoiceRecorder(AudioRecorder(context), directory, backgroundScope, testScheduler.timeSource)

    private fun TestScope.advanceBy(duration: Duration) {
        advanceTimeBy(duration)
        runCurrent()
    }

    private fun TestScope.collectAmplitudes(recorder: VoiceRecorder): List<Int?> {
        val samples = mutableListOf<Int?>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { recorder.amplitudes.toList(samples) }
        return samples
    }

    private fun VoiceRecorder.consumeOrNull(): File? {
        var consumed: File? = null
        val handedOver = consumeRecording { consumed = it }
        assertThat("consumeRecording result", handedOver, equalTo(consumed != null))
        return consumed
    }

    private fun TestScope.assertStartFailsWithoutFile() {
        val recorder = createRecorder()
        assertThat("start", recorder.start(), equalTo(false))
        assertNoRecording(recorder)
    }

    private fun TestScope.assertStartReplacesThePreviousRecording(afterFirstStart: (VoiceRecorder) -> Unit) {
        val recorder = createRecorder()
        recorder.start()
        afterFirstStart(recorder)
        val previous = mediaRecorderStub.outputFiles.single()

        assertThat("start again", recorder.start(), equalTo(true))

        val current = mediaRecorderStub.outputFiles.last()
        assertThat("previous recording deleted", previous.exists(), equalTo(false))
        assertThat(filesOnDisk(), equalTo(listOf(current)))
        assertThat(recorder.state.value, equalTo(Recording))
    }

    private fun TestScope.assertStopFailsWithoutFile() {
        val recorder = createRecorder()
        recorder.start()
        assertThat("stop", recorder.stop(), equalTo(false))
        assertNoRecording(recorder)
    }

    private fun filesOnDisk(): List<File> = recordingsDirectory.listFiles()?.toList().orEmpty()

    private fun assertNoRecording(recorder: VoiceRecorder) {
        assertThat(recorder.state.value, equalTo(Idle))
        assertThat("files in $recordingsDirectory", filesOnDisk(), empty())
    }

    companion object {
        private val RECORDING_TIME = 800.milliseconds
        private const val AMPLITUDE = 1234
    }
}
