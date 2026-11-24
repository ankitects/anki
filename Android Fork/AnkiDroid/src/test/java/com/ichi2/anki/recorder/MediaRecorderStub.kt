// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder

import android.content.Context
import android.media.MediaRecorder
import com.ichi2.anki.compat.Compat
import com.ichi2.anki.compat.CompatHelper
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkObject
import java.io.File

/**
 * Fakes the [MediaRecorder] that [AudioRecorder] gets from [CompatHelper]: it writes placeholder
 * bytes to the output file instead of recording, and creating, starting or stopping it can be made
 * to fail. Call [install] before creating the recorder and [uninstall] after the test.
 */
class MediaRecorderStub {
    val mediaRecorder: MediaRecorder = mockk(relaxed = true)

    val outputFiles: List<File>
        field = mutableListOf<File>()

    var failCreate = false
    var failStart = false
    var failStop = false
    var bytesOnStart = 32
    var bytesOnStop = 256

    init {
        every { mediaRecorder.setOutputFile(any<String>()) } answers { outputFiles += File(firstArg<String>()) }
        every { mediaRecorder.start() } answers {
            if (failStart) throw RuntimeException("start failed")
            outputFiles.last().appendBytes(ByteArray(bytesOnStart))
        }
        every { mediaRecorder.stop() } answers {
            if (failStop) throw RuntimeException("stop failed")
            outputFiles.last().appendBytes(ByteArray(bytesOnStop))
        }
    }

    fun install() {
        val real = CompatHelper.compat
        mockkObject(CompatHelper)
        every { CompatHelper.compat } returns
            object : Compat by real {
                override fun getMediaRecorder(context: Context): MediaRecorder {
                    if (failCreate) throw RuntimeException("Unable to initialize media recorder")
                    return mediaRecorder
                }
            }
    }

    fun uninstall() {
        unmockkObject(CompatHelper)
    }
}
