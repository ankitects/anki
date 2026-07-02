/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
