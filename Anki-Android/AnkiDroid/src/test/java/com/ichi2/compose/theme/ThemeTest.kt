// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.theme

import android.content.Context
import android.view.ContextThemeWrapper
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import com.ichi2.anki.common.android.R as CommonR

/**
 * Verifies that the four AnkiDroid XML themes (light, plain, dark, black) are
 * correctly bridged into a Material3 [androidx.compose.material3.ColorScheme]
 * by [toMaterial3ColorScheme].
 *
 * The colors expected here are pinned to the values declared in the matching
 * theme XML under res/values/theme_*.xml. If a theme's primary color is
 * intentionally changed, the matching assertion here should be updated.
 */
@RunWith(RobolectricTestRunner::class)
class ThemeTest {
    private val appContext: Context get() = ApplicationProvider.getApplicationContext()

    private fun themed(themeRes: Int) = ContextThemeWrapper(appContext, themeRes)

    @Test
    fun `light theme bridges colorPrimary to scheme primary`() {
        val scheme = themed(R.style.Theme_Light).toMaterial3ColorScheme()
        val expected = ContextCompat.getColor(appContext, CommonR.color.material_light_blue_500)
        assertEquals(expected, scheme.primary.toArgb())
    }

    @Test
    fun `dark theme bridges colorPrimary to scheme primary`() {
        val scheme = themed(R.style.Theme_Dark).toMaterial3ColorScheme()
        val expected = ContextCompat.getColor(appContext, CommonR.color.material_blue_400)
        assertEquals(expected, scheme.primary.toArgb())
    }

    @Test
    fun `plain theme produces a different primary than light theme`() {
        val light = themed(R.style.Theme_Light).toMaterial3ColorScheme()
        val plain = themed(R.style.Theme_Light_Plain).toMaterial3ColorScheme()
        assertNotEquals(
            light.primary.toArgb(),
            plain.primary.toArgb(),
            "Plain theme should have its own brand color distinct from Light theme",
        )
    }

    @Test
    fun `black theme produces a different surface than dark theme`() {
        val dark = themed(R.style.Theme_Dark).toMaterial3ColorScheme()
        val black = themed(R.style.Theme_Dark_Black).toMaterial3ColorScheme()
        assertNotEquals(
            dark.surface.toArgb(),
            black.surface.toArgb(),
            "Black theme should be visually distinct from Dark theme on surface color",
        )
    }

    @Test
    fun `light theme uses lightColorScheme as base for unspecified slots`() {
        val scheme = themed(R.style.Theme_Light).toMaterial3ColorScheme()
        val lightDefaults = lightColorScheme()
        // inverseSurface is not overridden by Theme_Light, so it should come from the base
        assertEquals(lightDefaults.inverseSurface.toArgb(), scheme.inverseSurface.toArgb())
    }

    @Test
    fun `dark theme uses darkColorScheme as base for unspecified slots`() {
        val scheme = themed(R.style.Theme_Dark).toMaterial3ColorScheme()
        val darkDefaults = darkColorScheme()
        // inverseSurface is not overridden by Theme_Dark, so it should come from the base
        assertEquals(darkDefaults.inverseSurface.toArgb(), scheme.inverseSurface.toArgb())
    }
}
