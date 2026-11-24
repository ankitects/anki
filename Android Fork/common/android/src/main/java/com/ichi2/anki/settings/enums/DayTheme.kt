// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import android.content.Context
import android.content.res.Configuration
import android.view.ContextThemeWrapper
import androidx.annotation.StyleRes
import com.ichi2.anki.common.android.R

sealed interface Theme : PrefEnum {
    @get:StyleRes
    val styleResId: Int
}

fun Theme.themedContext(base: Context): Context =
    ContextThemeWrapper(base, styleResId).apply {
        val nightMode = if (this@themedContext is NightTheme) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        applyOverrideConfiguration(
            Configuration(base.resources.configuration).apply {
                uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
            },
        )
    }

/** [R.array.day_theme_values] */
enum class DayTheme(
    override val entryResId: Int,
    override val styleResId: Int,
) : Theme {
    LIGHT(R.string.theme_light_value, R.style.Theme_AnkiDroid),
    PLAIN(R.string.theme_plain_value, R.style.Theme_AnkiDroid_Plain),
    EINK(R.string.theme_eink_scheme_value, R.style.Theme_AnkiDroid_Eink),
}
