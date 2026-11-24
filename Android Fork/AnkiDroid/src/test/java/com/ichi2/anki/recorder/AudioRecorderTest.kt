// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import android.content.Context
import android.media.MediaRecorder
import com.ichi2.anki.compat.CompatHelper
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.junit5.JUnit5Asserter.assertNotNull

class AudioRecorderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val context =
        mockk<Context> {
            every { cacheDir } answers { tempFolder.root }
        }

    private val mockMediaRecorder = mockk<MediaRecorder>(relaxed = true)

    private fun createRecorder(): AudioRecorder {
        mockkObject(CompatHelper)
        every { CompatHelper.compat.getMediaRecorder(any()) } returns mockMediaRecorder
        return AudioRecorder(context)
    }

    @After
    fun teardown() {
        unmockkObject(CompatHelper)
        clearAllMocks()
    }

    @Test
    fun `start should set isRecording to true and configure recorder`() {
        val audioRecorder = createRecorder()
        audioRecorder.start()

        assertTrue(audioRecorder.isRecording)
        assertNotNull("The file should still be initialized", audioRecorder.currentFile)

        verify { mockMediaRecorder.prepare() }
        verify { mockMediaRecorder.start() }
    }

    @Test
    fun `stop should set isRecording to false`() {
        val audioRecorder = createRecorder()
        audioRecorder.start()
        audioRecorder.stop()

        assertFalse(audioRecorder.isRecording)
        verify { mockMediaRecorder.stop() }
    }

    @Test
    fun `close should release media recorder resources`() {
        val audioRecorder = createRecorder()
        audioRecorder.start()

        audioRecorder.close()

        verify { mockMediaRecorder.release() }
    }

    @Test
    fun `start should fallback to AMR if AAC configuration fails`() {
        every { mockMediaRecorder.prepare() } throws Exception("AAC not supported") andThen Unit

        val audioRecorder = createRecorder()
        audioRecorder.start()

        verify { mockMediaRecorder.reset() }
        verify(exactly = 2) { mockMediaRecorder.prepare() }

        assertTrue(audioRecorder.isRecording)
    }

    @Test
    fun `isRecording should remain false if mediaRecorder start fails`() {
        every { mockMediaRecorder.start() } throws RuntimeException("Hardware failure")

        val audioRecorder = createRecorder()
        audioRecorder.start()

        assertFalse(audioRecorder.isRecording)
        assertNotNull("The file should still be initialized", audioRecorder.currentFile)
    }

    @Test
    fun `start should do nothing if already recording`() {
        val audioRecorder = createRecorder()
        audioRecorder.start()
        audioRecorder.start()

        verify(exactly = 1) { mockMediaRecorder.start() }
    }

    @Test
    fun `pause and resume should call hardware recorder`() {
        val audioRecorder = createRecorder()
        audioRecorder.start()

        audioRecorder.pause()
        verify { mockMediaRecorder.pause() }

        audioRecorder.resume()
        verify { mockMediaRecorder.resume() }
    }

    @Test
    fun `start should use provided file instead of temp file`() {
        val customFile = File(tempFolder.root, "custom_audio.3gp")

        val audioRecorder = createRecorder()
        audioRecorder.start(customFile)

        assertEquals(customFile.absolutePath, audioRecorder.currentFile?.absolutePath)
        verify { mockMediaRecorder.setOutputFile(customFile.absolutePath) }
    }

    @Test
    fun `stop should delete temp file if not kept`() {
        val audioRecorder = createRecorder()
        audioRecorder.start()

        val tempFile = audioRecorder.currentFile
        assertTrue("Temp file should exist", tempFile?.exists() ?: false)

        audioRecorder.stop(keepFile = false)
        assertFalse(tempFile?.exists() ?: true)
    }

    @Test
    fun `close() while recording temp file should delete the file`() {
        val audioRecorder = createRecorder()
        audioRecorder.start()
        val tempFile = audioRecorder.currentFile

        assertTrue("Temp file should exist", tempFile?.exists() ?: false)

        audioRecorder.close()

        assertFalse(tempFile?.exists() ?: true, "Temp file should be cleaned up on close")
        verify { mockMediaRecorder.release() }
    }

    @Test
    fun `recording after a failed stop should still work and clean up`() {
        every { mockMediaRecorder.stop() } throws RuntimeException("stop failed")

        val audioRecorder = createRecorder()
        audioRecorder.start()
        val firstFile = audioRecorder.currentFile

        audioRecorder.stop()

        assertFalse(firstFile?.exists() ?: true, "File should be deleted if recorder.stop() fails")
        assertFalse(audioRecorder.isRecording)

        // Ensure we can start again immediately
        audioRecorder.start()
        assertTrue(audioRecorder.isRecording)
        assertNotNull(audioRecorder.currentFile)
    }
}
