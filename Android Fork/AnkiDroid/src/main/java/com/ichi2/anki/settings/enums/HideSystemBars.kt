// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import androidx.annotation.StringRes
import com.ichi2.anki.R

/** [R.array.hide_system_bars_values] */
enum class HideSystemBars(
    @StringRes
    override val entryResId: Int,
) : PrefEnum {
    NONE(R.string.hide_system_bars_none_value),
    STATUS_BAR(R.string.hide_system_bars_status_bar_value),
    NAVIGATION_BAR(R.string.hide_system_bars_navigation_bar_value),
    ALL(R.string.hide_system_bars_all_value),
}
