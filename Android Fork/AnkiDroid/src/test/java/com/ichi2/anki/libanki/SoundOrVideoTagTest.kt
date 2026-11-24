// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.SoundOrVideoTag.Type
import com.ichi2.anki.libanki.SoundOrVideoTag.Type.VIDEO
import com.ichi2.anki.multimedia.getTagType
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SoundOrVideoTagTest {
    @get:Rule
    val mediaDirectory = TemporaryFolder()

    @Test
    fun mp3IsAudio() {
        val tag = SoundOrVideoTag("test.mp3")
        assertThat(tag.getTagType(mediaDirectory.root), equalTo(Type.AUDIO))
    }

    @Test
    fun audioIsDefault() {
        // if we don't know, assume it's audio
        // When we want to play a tag, the audio player (Android) can handle a failure
        // better than the video player (HTML)
        val tag = SoundOrVideoTag("test.txt")
        assertThat(tag.getTagType(mediaDirectory.root), equalTo(Type.AUDIO))
    }

    @Test
    fun mp4IsVideo() {
        // Note: This will go out to the filesystem. As it doesn't exist, assume it's a video
        val tag = SoundOrVideoTag("test.mp4")
        assertThat(tag.getTagType(mediaDirectory.root), equalTo(VIDEO))
    }
}
