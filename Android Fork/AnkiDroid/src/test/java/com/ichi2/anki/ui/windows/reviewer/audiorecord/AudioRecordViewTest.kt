// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Alok Srivastava <alok020505@gmail.com>

package com.ichi2.anki.ui.windows.reviewer.audiorecord

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.CommonString
import com.ichi2.anki.databinding.ViewAudioRecordBinding
import com.ichi2.themes.Themes
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Test of [AudioRecordView] */
@RunWith(AndroidJUnit4::class)
class AudioRecordViewTest {
    private lateinit var context: Context
    private lateinit var audioRecordView: AudioRecordView
    private val binding by lazy { ViewAudioRecordBinding.bind(audioRecordView) }

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        Themes.setTheme(context)
        audioRecordView = AudioRecordView(context)
    }

    @Test
    fun `record button exposes a label to TalkBack`() {
        assertEquals(context.getString(CommonString.record_voice), binding.recordButton.contentDescription)
    }
}
