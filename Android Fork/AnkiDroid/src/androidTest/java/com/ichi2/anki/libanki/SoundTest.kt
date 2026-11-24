// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.multimedia.isAudioFileInVideoContainer
import com.ichi2.anki.tests.InstrumentedTest
import com.ichi2.anki.tests.Shared
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoundTest : InstrumentedTest() {
    @Test
    fun mp4IsDetected() {
        val mp4 = Shared.getTestFile(testContext, "anki-15872-valid-1.mp4")
        assertThat("mp4 with video should be marked as video", isAudioFileInVideoContainer(mp4), equalTo(false))
    }

    @Test
    fun audioOnlyMp4IsDetected() {
        val mp4 = Shared.getTestFile(testContext, "anki-15872-audio-only.mp4")
        assertThat("mp4 should be audio only", isAudioFileInVideoContainer(mp4), equalTo(true))
    }

    @Test
    fun audioOnlyWebmIsDetected() {
        val mp4 = Shared.getTestFile(testContext, "anki-15872-audio-only.webm")
        assertThat("webm should be audio only", isAudioFileInVideoContainer(mp4), equalTo(true))
    }
}
