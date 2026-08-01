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
package com.ichi2.anki.cardviewer

import android.media.MediaPlayer
import android.net.Uri
import androidx.core.net.toFile
import com.ichi2.anki.AndroidTtsError
import com.ichi2.anki.libanki.TtsPlayer
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.spyk
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.File

class MediaErrorHandlerJvmTest {
    @MockK
    lateinit var onMediaError: (String) -> Unit

    @MockK
    lateinit var onTtsError: (TtsPlayer.TtsError) -> Unit

    private lateinit var handler: MediaErrorHandler

    @Before
    fun setUp() {
        MockKAnnotations.init(this)
        mockkStatic("androidx.core.net.UriKt")

        handler = spyk(MediaErrorHandler())
        handler.onMediaError = onMediaError
        handler.onTtsError = onTtsError
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `onError with non-file scheme returns CONTINUE_MEDIA`() {
        val uri = mockk<Uri>()
        every { uri.scheme } returns "http"

        val result = handler.onError(uri)

        assertEquals(MediaErrorBehavior.CONTINUE_MEDIA, result)
        verify(exactly = 0) { handler.processMissingMedia(any(), any()) }
    }

    @Test
    fun `onError with file scheme and existing file returns RETRY_MEDIA`() {
        val uri = mockk<Uri>()
        val file = mockk<File>()

        every { uri.scheme } returns "file"
        every { uri.toFile() } returns file
        every { file.exists() } returns true

        val result = handler.onError(uri)

        assertEquals(MediaErrorBehavior.RETRY_MEDIA, result)
        verify(exactly = 0) { handler.processMissingMedia(any(), any()) }
    }

    @Test
    fun `onError with file scheme and missing file calls callback and returns CONTINUE_MEDIA`() {
        val uri = mockk<Uri>()
        val file = mockk<File>()
        val fileName = "test.mp3"

        every { uri.scheme } returns "file"
        every { uri.toFile() } returns file
        every { file.exists() } returns false
        every { file.name } returns fileName
        every { onMediaError(any()) } just runs

        val result = handler.onError(uri)

        assertEquals(MediaErrorBehavior.CONTINUE_MEDIA, result)
        verify { handler.processMissingMedia(file, any()) }
        verify { onMediaError(fileName) }
    }

    @Test
    fun `onMediaPlayerError delegates to onError`() {
        val uri = mockk<Uri>()
        val file = mockk<File>()

        every { uri.scheme } returns "file"
        every { uri.toFile() } returns file
        every { file.exists() } returns true

        val mp = mockk<MediaPlayer>()
        val result = handler.onMediaPlayerError(mp, 100, 200, uri)

        assertEquals(MediaErrorBehavior.RETRY_MEDIA, result)
    }

    @Test
    fun `onTtsError triggers callback`() {
        val error = AndroidTtsError.InvalidVoiceError
        val isAutomatic = true

        every { onTtsError(any()) } just runs

        handler.onTtsError(error, isAutomatic)
        verify { onTtsError(error) }
    }
}
