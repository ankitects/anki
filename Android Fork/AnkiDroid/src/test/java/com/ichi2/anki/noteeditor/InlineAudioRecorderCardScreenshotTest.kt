// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.noteeditor

import com.ichi2.anki.ComposeScreenshotTest
import com.ichi2.anki.ThemeConfig
import org.junit.Test
import org.robolectric.annotation.Config

class InlineAudioRecorderCardScreenshotTest : ComposeScreenshotTest() {
    @Test
    fun states() =
        capture("inline_recorder_states") {
            InlineAudioRecorderStates(animated = theme != ThemeConfig.EINK)
        }

    @Test
    @Config(qualifiers = "w320dp-h640dp-mdpi")
    fun largeText() =
        capture("inline_recorder_states_large_text", fontScale = 2f) {
            InlineAudioRecorderStates(animated = theme != ThemeConfig.EINK)
        }
}
