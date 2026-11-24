// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.compose.ui.preview

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme

/** Supplies every app theme to a preview wrapped in [AnkiDroidPreview]. */
class AnkiDroidThemeProvider : PreviewParameterProvider<Theme> {
    override val values: Sequence<Theme> = DayTheme.entries.asSequence() + NightTheme.entries.asSequence()

    override fun getDisplayName(index: Int): String =
        when (val theme = values.elementAt(index)) {
            DayTheme.EINK -> "E-ink"
            NightTheme.EINK -> "E-ink night"
            else -> theme.toString().lowercase().replaceFirstChar(Char::titlecase)
        }
}
