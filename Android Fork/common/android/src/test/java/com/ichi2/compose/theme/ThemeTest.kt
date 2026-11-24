// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.theme

import android.content.Context
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.test.core.app.ApplicationProvider
import com.ichi2.anki.common.android.R
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.anki.settings.enums.themedContext
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

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

    private fun themed(theme: Theme) = theme.themedContext(appContext)

    @Test
    @Config(sdk = [24, Config.TARGET_SDK])
    fun `surface containers are opaque in every app theme`() {
        for (theme in DayTheme.entries + NightTheme.entries) {
            val scheme = themed(theme).toMaterial3ColorScheme()
            for (color in listOf(
                scheme.surfaceContainer,
                scheme.surfaceContainerHigh,
                scheme.surfaceContainerHighest,
                scheme.surfaceContainerLow,
                scheme.surfaceContainerLowest,
            )) {
                assertEquals(1f, color.alpha, "${theme.name}: popup surfaces must hide underlying content")
            }
        }
    }

    @Test
    fun `light theme bridges colorPrimary to scheme primary`() {
        val scheme = themed(DayTheme.LIGHT).toMaterial3ColorScheme()
        val expected = ContextCompat.getColor(appContext, R.color.material_light_blue_500)
        assertEquals(expected, scheme.primary.toArgb())
    }

    @Test
    fun `dark theme bridges colorPrimary to scheme primary`() {
        val scheme = themed(NightTheme.DARK).toMaterial3ColorScheme()
        val expected = ContextCompat.getColor(appContext, R.color.material_blue_400)
        assertEquals(expected, scheme.primary.toArgb())
    }

    @Test
    fun `plain theme produces a different primary than light theme`() {
        val light = themed(DayTheme.LIGHT).toMaterial3ColorScheme()
        val plain = themed(DayTheme.PLAIN).toMaterial3ColorScheme()
        assertNotEquals(
            light.primary.toArgb(),
            plain.primary.toArgb(),
            "Plain theme should have its own brand color distinct from Light theme",
        )
    }

    @Test
    fun `black theme produces a different surface than dark theme`() {
        val dark = themed(NightTheme.DARK).toMaterial3ColorScheme()
        val black = themed(NightTheme.BLACK).toMaterial3ColorScheme()
        assertNotEquals(
            dark.surface.toArgb(),
            black.surface.toArgb(),
            "Black theme should be visually distinct from Dark theme on surface color",
        )
    }

    @Test
    fun `light theme uses lightColorScheme as base for unspecified slots`() {
        val scheme = themed(DayTheme.LIGHT).toMaterial3ColorScheme()
        val lightDefaults = lightColorScheme()
        // surfaceTint is not overridden by Light, so it should come from the base
        assertEquals(lightDefaults.surfaceTint.toArgb(), scheme.surfaceTint.toArgb())
    }

    @Test
    fun `dark theme uses darkColorScheme as base for unspecified slots`() {
        val scheme = themed(NightTheme.DARK).toMaterial3ColorScheme()
        val darkDefaults = darkColorScheme()
        // surfaceTint is not overridden by Dark, so it should come from the base
        assertEquals(darkDefaults.surfaceTint.toArgb(), scheme.surfaceTint.toArgb())
    }

    @Test
    fun `dark theme bridges the inverse colors used by snackbars`() {
        val scheme = themed(NightTheme.DARK).toMaterial3ColorScheme()
        assertEquals(0xFFCFCFCF.toInt(), scheme.inverseSurface.toArgb())
        assertEquals(ContextCompat.getColor(appContext, R.color.material_theme_grey), scheme.inverseOnSurface.toArgb())
        assertEquals(ContextCompat.getColor(appContext, R.color.material_light_blue_900), scheme.inversePrimary.toArgb())
    }

    @Test
    fun `light theme bridges fabNormalColor to the fab container color`() {
        val context = themed(DayTheme.LIGHT)
        val colors = context.toAnkiDroidColors(context.toMaterial3ColorScheme())
        val expected = ContextCompat.getColor(appContext, R.color.material_light_blue_700)
        assertEquals(expected, colors.fabContainer.toArgb())
    }

    @Test
    fun `black theme bridges its own fabNormalColor to the fab container color`() {
        val context = themed(NightTheme.BLACK)
        val colors = context.toAnkiDroidColors(context.toMaterial3ColorScheme())
        assertEquals(0xFF303030.toInt(), colors.fabContainer.toArgb())
    }

    @Test
    fun `fab content color follows the theme onPrimary`() {
        val context = themed(DayTheme.LIGHT)
        val scheme = context.toMaterial3ColorScheme()
        assertEquals(scheme.onPrimary.toArgb(), context.toAnkiDroidColors(scheme).onFab.toArgb())
    }

    @Test
    fun `light theme bridges the answer button colors`() {
        val context = themed(DayTheme.LIGHT)
        val colors = context.toAnkiDroidColors(context.toMaterial3ColorScheme())
        assertEquals(0xFFC8E6C9.toInt(), colors.goodButton.toArgb())
        assertEquals(ContextCompat.getColor(appContext, R.color.material_green_900), colors.onGoodButton.toArgb())
        assertEquals(ContextCompat.getColor(appContext, R.color.material_blue_700), colors.showAnswerButton.toArgb())
    }

    @Test
    fun `study screen elevation follows the theme`() {
        assertEquals(0.8f, themed(DayTheme.LIGHT).toAnkiDroidDimensions().studyScreenElevation.value, absoluteTolerance = 0.001f)
        assertEquals(0.dp, themed(DayTheme.EINK).toAnkiDroidDimensions().studyScreenElevation)
    }
}
