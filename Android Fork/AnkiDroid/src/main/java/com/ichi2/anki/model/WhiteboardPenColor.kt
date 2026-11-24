// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

import androidx.annotation.CheckResult
import com.ichi2.themes.Themes

class WhiteboardPenColor(
    val lightPenColor: Int?,
    val darkPenColor: Int?,
) {
    fun fromPreferences(): Int? =
        if (Themes.isNightTheme) {
            darkPenColor
        } else {
            lightPenColor
        }

    companion object {
        @get:CheckResult
        val default: WhiteboardPenColor
            get() = WhiteboardPenColor(null, null)
    }
}
