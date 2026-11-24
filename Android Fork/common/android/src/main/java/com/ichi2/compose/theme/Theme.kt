// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>

package com.ichi2.compose.theme

import android.content.Context
import android.content.res.TypedArray
import androidx.annotation.StyleableRes
import androidx.annotation.VisibleForTesting
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.res.use
import androidx.core.content.withStyledAttributes
import com.ichi2.anki.common.android.R

/**
 * Wraps [content] in a Material3 [MaterialTheme] populated from the currently
 * applied AnkiDroid XML theme (light, plain, dark or black).
 *
 * Color attributes are read from the host Activity's theme via the
 * [R.styleable.ComposeTheme] styleable, so a theme change in app settings
 * (which already triggers Activity.recreate()) flows through automatically.
 *
 * For any color slot not defined by the active theme, the Material3 default is used.
 *
 * Wrap all top-level Compose content in this composable so screens pick up the
 * same colors as the rest of the app.
 */
@Composable
fun AnkiDroidTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colorScheme = remember(context) { context.toMaterial3ColorScheme() }
    val ankiColors = remember(context, colorScheme) { context.toAnkiDroidColors(colorScheme) }
    val ankiDimensions = remember(context) { context.toAnkiDroidDimensions() }
    CompositionLocalProvider(
        LocalDimensions provides Dimensions(),
        LocalAnkiDroidDimensions provides ankiDimensions,
        LocalAnkiDroidColors provides ankiColors,
    ) {
        MaterialTheme(colorScheme = colorScheme, content = content)
    }
}

/**
 * Reads the [AnkiDroidColors] extras from the active XML theme. The View
 * screens tint their FABs with the fabNormalColor attr and colorOnPrimary, so
 * Compose FABs follow the same pair to match them.
 */
@VisibleForTesting
internal fun Context.toAnkiDroidColors(scheme: ColorScheme): AnkiDroidColors {
    val defaults = scheme.toDefaultAnkiDroidColors()
    var colors = defaults
    withStyledAttributes(attrs = R.styleable.ComposeTheme) {
        colors =
            AnkiDroidColors(
                fabContainer = color(R.styleable.ComposeTheme_fabNormalColor, defaults.fabContainer),
                onFab = scheme.onPrimary,
                showAnswerButton = color(R.styleable.ComposeTheme_showAnswerButtonBackground, defaults.showAnswerButton),
                againButton = color(R.styleable.ComposeTheme_againBackgroundColor, defaults.againButton),
                onAgainButton = color(R.styleable.ComposeTheme_againTextColor, defaults.onAgainButton),
                hardButton = color(R.styleable.ComposeTheme_hardBackgroundColor, defaults.hardButton),
                onHardButton = color(R.styleable.ComposeTheme_hardTextColor, defaults.onHardButton),
                goodButton = color(R.styleable.ComposeTheme_goodBackgroundColor, defaults.goodButton),
                onGoodButton = color(R.styleable.ComposeTheme_goodTextColor, defaults.onGoodButton),
                easyButton = color(R.styleable.ComposeTheme_easyBackgroundColor, defaults.easyButton),
                onEasyButton = color(R.styleable.ComposeTheme_easyTextColor, defaults.onEasyButton),
            )
    }
    return colors
}

@VisibleForTesting
internal fun Context.toAnkiDroidDimensions(): AnkiDroidDimensions {
    val elevationPx =
        obtainStyledAttributes(R.styleable.ComposeTheme).use {
            it.getDimension(R.styleable.ComposeTheme_studyScreenElevation, 0f)
        }
    return AnkiDroidDimensions(studyScreenElevation = (elevationPx / resources.displayMetrics.density).dp)
}

@VisibleForTesting
internal fun Context.toMaterial3ColorScheme(): ColorScheme {
    var scheme: ColorScheme = lightColorScheme()
    withStyledAttributes(attrs = R.styleable.ComposeTheme) {
        val isLight = getBoolean(R.styleable.ComposeTheme_isLightTheme, true)
        val base = if (isLight) lightColorScheme() else darkColorScheme()
        val surface = color(R.styleable.ComposeTheme_colorSurface, base.surface)
        scheme =
            base.copy(
                primary = color(R.styleable.ComposeTheme_colorPrimary, base.primary),
                onPrimary = color(R.styleable.ComposeTheme_colorOnPrimary, base.onPrimary),
                primaryContainer = color(R.styleable.ComposeTheme_colorPrimaryContainer, base.primaryContainer),
                onPrimaryContainer = color(R.styleable.ComposeTheme_colorOnPrimaryContainer, base.onPrimaryContainer),
                secondary = color(R.styleable.ComposeTheme_colorSecondary, base.secondary),
                onSecondary = color(R.styleable.ComposeTheme_colorOnSecondary, base.onSecondary),
                secondaryContainer = color(R.styleable.ComposeTheme_colorSecondaryContainer, base.secondaryContainer),
                onSecondaryContainer = color(R.styleable.ComposeTheme_colorOnSecondaryContainer, base.onSecondaryContainer),
                tertiary = color(R.styleable.ComposeTheme_colorTertiary, base.tertiary),
                onTertiary = color(R.styleable.ComposeTheme_colorOnTertiary, base.onTertiary),
                tertiaryContainer = color(R.styleable.ComposeTheme_colorTertiaryContainer, base.tertiaryContainer),
                onTertiaryContainer = color(R.styleable.ComposeTheme_colorOnTertiaryContainer, base.onTertiaryContainer),
                error = color(R.styleable.ComposeTheme_colorError, base.error),
                onError = color(R.styleable.ComposeTheme_colorOnError, base.onError),
                errorContainer = color(R.styleable.ComposeTheme_colorErrorContainer, base.errorContainer),
                onErrorContainer = color(R.styleable.ComposeTheme_colorOnErrorContainer, base.onErrorContainer),
                surface = surface,
                onSurface = color(R.styleable.ComposeTheme_colorOnSurface, base.onSurface),
                surfaceVariant = color(R.styleable.ComposeTheme_colorSurfaceVariant, base.surfaceVariant),
                onSurfaceVariant = color(R.styleable.ComposeTheme_colorOnSurfaceVariant, base.onSurfaceVariant),
                outline = color(R.styleable.ComposeTheme_colorOutline, base.outline),
                outlineVariant = color(R.styleable.ComposeTheme_colorOutlineVariant, base.outlineVariant),
                inverseSurface = color(R.styleable.ComposeTheme_colorSurfaceInverse, base.inverseSurface),
                inverseOnSurface = color(R.styleable.ComposeTheme_colorOnSurfaceInverse, base.inverseOnSurface),
                inversePrimary = color(R.styleable.ComposeTheme_colorPrimaryInverse, base.inversePrimary),
                surfaceContainer = color(R.styleable.ComposeTheme_colorSurfaceContainer, base.surfaceContainer),
                surfaceContainerHigh =
                    color(
                        R.styleable.ComposeTheme_colorSurfaceContainerHigh,
                        base.surfaceContainerHigh,
                    ),
                surfaceContainerHighest =
                    color(
                        R.styleable.ComposeTheme_colorSurfaceContainerHighest,
                        base.surfaceContainerHighest,
                    ),
                surfaceContainerLow =
                    color(
                        R.styleable.ComposeTheme_colorSurfaceContainerLow,
                        base.surfaceContainerLow,
                    ),
                surfaceContainerLowest =
                    color(
                        R.styleable.ComposeTheme_colorSurfaceContainerLowest,
                        base.surfaceContainerLowest,
                    ),
                surfaceDim = color(R.styleable.ComposeTheme_colorSurfaceDim, base.surfaceDim),
                surfaceBright = color(R.styleable.ComposeTheme_colorSurfaceBright, base.surfaceBright),
                background = color(R.styleable.ComposeTheme_android_colorBackground, base.background),
                onBackground = color(R.styleable.ComposeTheme_colorOnBackground, base.onBackground),
            )
    }
    return scheme
}

private fun TypedArray.color(
    @StyleableRes index: Int,
    fallback: Color,
): Color = if (hasValue(index)) Color(getColor(index, fallback.toArgb())) else fallback
