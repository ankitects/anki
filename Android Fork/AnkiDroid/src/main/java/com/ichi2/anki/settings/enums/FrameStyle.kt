// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.settings.enums

import androidx.annotation.StringRes
import com.ichi2.anki.R

/** [R.array.reviewer_frame_style_values] */
enum class FrameStyle(
    @StringRes
    override val entryResId: Int,
) : PrefEnum {
    CARD(R.string.reviewer_frame_style_card_value),
    BOX(R.string.reviewer_frame_style_box_value),
}
