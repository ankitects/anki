// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import com.ichi2.anki.common.android.R

/** [R.array.app_theme_values] */
enum class AppTheme(
    override val entryResId: Int,
) : PrefEnum {
    FOLLOW_SYSTEM(R.string.theme_follow_system_value),
    DAY(R.string.theme_day_scheme_value),
    NIGHT(R.string.theme_night_scheme_value),
}
