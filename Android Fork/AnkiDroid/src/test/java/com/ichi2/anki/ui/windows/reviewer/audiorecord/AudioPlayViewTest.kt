// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Alok Srivastava <alok020505@gmail.com>

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.R
import com.ichi2.anki.databinding.ViewAudioPlayBinding
import com.ichi2.themes.Themes
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import kotlin.test.assertEquals

/** Test of [AudioPlayView] */
@RunWith(AndroidJUnit4::class)
class AudioPlayViewTest {
    private lateinit var context: Context
    private lateinit var audioPlayView: AudioPlayView
    private val binding by lazy { ViewAudioPlayBinding.bind(audioPlayView) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Themes.setTheme(context)
        audioPlayView = AudioPlayView(context)
    }

    @Test
    fun `play button exposes a label to TalkBack`() {
        assertEquals(context.getString(CommonString.play_recording), binding.playButton.contentDescription)
    }

    @Test
    fun `cancel button exposes a label to TalkBack`() {
        assertEquals(context.getString(CommonString.dialog_cancel), binding.cancelButton.contentDescription)
    }

    @Test
    fun `changing to the replay icon updates the label for TalkBack`() {
        audioPlayView.changePlayIcon(R.drawable.ic_replay)
        shadowOf(Looper.getMainLooper()).idle()

        assertEquals(context.getString(CommonString.replay_voice), binding.playButton.contentDescription)
    }
}
