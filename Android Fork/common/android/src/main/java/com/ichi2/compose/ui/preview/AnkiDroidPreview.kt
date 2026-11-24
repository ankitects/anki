// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.preview

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme
import com.ichi2.anki.settings.enums.themedContext
import com.ichi2.compose.theme.AnkiDroidTheme

/**
 * Applies [theme] to preview content, defaulting to the preview's light or dark UI mode.
 *
 * Use [AnkiDroidThemeProvider] to preview every app theme, or [ThemePreviews] for light and dark.
 */
@Composable
fun AnkiDroidPreview(
    theme: Theme = if (isSystemInDarkTheme()) NightTheme.DARK else DayTheme.LIGHT,
    content: @Composable () -> Unit,
) {
    // AnkiDroidTheme reads XML attributes, so changing only the preview's UI mode is insufficient.
    val context = LocalContext.current
    val themedContext = remember(context, theme) { theme.themedContext(context) }
    CompositionLocalProvider(LocalContext provides themedContext) {
        AnkiDroidTheme(content = content)
    }
}
