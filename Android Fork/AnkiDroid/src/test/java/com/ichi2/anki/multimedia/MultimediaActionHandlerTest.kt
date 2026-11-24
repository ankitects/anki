// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.multimedia

import com.ichi2.anki.multimedia.MultimediaBottomSheet.MultimediaAction
import com.ichi2.anki.multimediacard.fields.AudioRecordingField
import com.ichi2.anki.multimediacard.fields.ImageField
import com.ichi2.anki.multimediacard.fields.MediaClipField
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.not
import org.hamcrest.Matchers.sameInstance
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class MultimediaActionHandlerTest {
    @ParameterizedTest
    @EnumSource(MultimediaAction::class)
    fun `every action resolves to a handler bound to that action`(action: MultimediaAction) {
        val handler = MultimediaActionHandler.forAction(action)
        assertThat(handler.action, equalTo(action))
    }

    @Test
    fun `image-file handler creates an ImageField`() {
        assertThat(MultimediaActionHandler.ImageFile.createField(), instanceOf(ImageField::class.java))
    }

    @Test
    fun `camera handler creates an ImageField`() {
        assertThat(MultimediaActionHandler.Camera.createField(), instanceOf(ImageField::class.java))
    }

    @Test
    fun `drawing handler creates an ImageField`() {
        assertThat(MultimediaActionHandler.Drawing.createField(), instanceOf(ImageField::class.java))
    }

    @Test
    fun `audio-clip handler creates a MediaClipField`() {
        assertThat(MultimediaActionHandler.AudioFile.createField(), instanceOf(MediaClipField::class.java))
    }

    @Test
    fun `video-clip handler creates a MediaClipField`() {
        assertThat(MultimediaActionHandler.VideoFile.createField(), instanceOf(MediaClipField::class.java))
    }

    @Test
    fun `audio-recording handler creates an AudioRecordingField`() {
        assertThat(
            MultimediaActionHandler.AudioRecording.createField(),
            instanceOf(AudioRecordingField::class.java),
        )
    }

    @Test
    fun `createField returns a fresh instance per call`() {
        val first = MultimediaActionHandler.ImageFile.createField()
        val second = MultimediaActionHandler.ImageFile.createField()
        assertThat(first, not(sameInstance(second)))
    }
}
