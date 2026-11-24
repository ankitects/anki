// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.anki.recorder.ui

import android.os.Build
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@SdkSuppress(minSdkVersion = Build.VERSION_CODES.O)
class RecordingWaveformTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `the first level is drawn at the start edge`() {
        val history = WaveformHistory().apply { add(WaveformHistory.MAX_AMPLITUDE.toInt()) }
        composeRule.setContent {
            Column {
                for (direction in LayoutDirection.entries) {
                    CompositionLocalProvider(LocalLayoutDirection provides direction) {
                        RecordingWaveform(history, Color.Black, Modifier.size(100.dp, 20.dp).testTag(direction.name))
                    }
                }
            }
        }

        assertEquals(listOf(true, false), barAtEdges(LayoutDirection.Ltr), "left to right")
        assertEquals(listOf(false, true), barAtEdges(LayoutDirection.Rtl), "right to left")
    }

    private fun barAtEdges(direction: LayoutDirection): List<Boolean> {
        val pixels = composeRule.onNodeWithTag(direction.name).captureToImage().toPixelMap()
        val nearTop = pixels.height / 8
        return listOf(pixels[1, nearTop].red < 0.5f, pixels[pixels.width - 2, nearTop].red < 0.5f)
    }
}
