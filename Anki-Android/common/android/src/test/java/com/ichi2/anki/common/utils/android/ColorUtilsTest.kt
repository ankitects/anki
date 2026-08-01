// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.android

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals

/** Tests for [darkenColor] and [lightenColorAbsolute] */
@RunWith(AndroidJUnit4::class)
class ColorUtilsTest {
    @Test
    fun darkenColor_withNoChange_returnsSameColor() {
        val white = Color.WHITE
        assertEquals(white, darkenColor(white, factor = 1.0f))
    }

    @Test
    fun darkenColor_withFullDarken_returnsBlack() {
        val white = Color.WHITE
        assertEquals(Color.BLACK, darkenColor(white, factor = 0.0f))
    }

    @Test
    fun lightenColorAbsolute_withNoChange_returnsSameColor() {
        val red = Color.RED
        assertEquals(red, lightenColorAbsolute(red, amount = 0.0f))
    }
}
