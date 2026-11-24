// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import com.ichi2.anki.R

/** [R.array.reviewer_toolbar_position_values] */
enum class ToolbarPosition(
    override val entryResId: Int,
) : PrefEnum {
    TOP(R.string.reviewer_toolbar_value_top),
    BOTTOM(R.string.reviewer_toolbar_value_bottom),
    NONE(R.string.reviewer_toolbar_value_none),
}
