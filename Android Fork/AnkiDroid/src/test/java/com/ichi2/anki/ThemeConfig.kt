// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.google.testing.junit.testparameterinjector.TestParameterValuesProvider
import com.ichi2.anki.settings.enums.DayTheme
import com.ichi2.anki.settings.enums.NightTheme
import com.ichi2.anki.settings.enums.Theme

/** A theme that screenshot tests can be run with */
enum class ThemeConfig(
    val theme: Theme,
) {
    LIGHT(DayTheme.LIGHT),
    PLAIN(DayTheme.PLAIN),
    DARK(NightTheme.DARK),
    BLACK(NightTheme.BLACK),
    EINK(DayTheme.EINK),
    EINK_NIGHT(NightTheme.EINK),
    ;

    val isNight get() = this == DARK || this == BLACK || this == EINK_NIGHT
}

/** Provides the themes selected with `-Ptheme`: a comma-separated list, or `all` */
class ThemeProvider : TestParameterValuesProvider() {
    override fun provideValues(context: Context?): List<ThemeConfig> {
        val requestedTheme = System.getProperty("screenshot.theme") ?: "light"
        val requestedThemes = requestedTheme.split(",").map { it.trim().lowercase() }
        if ("all" in requestedThemes) {
            return ThemeConfig.entries
        }
        return ThemeConfig.entries.filter { requestedThemes.contains(it.name.lowercase()) }
    }
}
