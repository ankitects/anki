// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Mani infinyte01@gmail.com

package com.ichi2.anki

import com.ichi2.anki.cardviewer.ViewerCommand

object AnkiDroidJsAPIConstants {
    // JS API ERROR CODE
    const val ANKI_JS_ERROR_CODE_ERROR: Int = -1
    const val ANKI_JS_ERROR_CODE_DEFAULT: Int = 0
    const val ANKI_JS_ERROR_CODE_MARK_CARD: Int = 1
    const val ANKI_JS_ERROR_CODE_FLAG_CARD: Int = 2

    const val ANKI_JS_ERROR_CODE_BURY_CARD: Int = 3
    const val ANKI_JS_ERROR_CODE_SUSPEND_CARD: Int = 4
    const val ANKI_JS_ERROR_CODE_BURT_NOTE: Int = 5
    const val ANKI_JS_ERROR_CODE_SUSPEND_NOTE: Int = 6
    const val ANKI_JS_ERROR_CODE_SET_DUE: Int = 7
    const val ANKI_JS_ERROR_CODE_SEARCH_CARD: Int = 8

    // js api developer contact
    const val CURRENT_JS_API_VERSION = "0.0.3"
    const val MINIMUM_JS_API_VERSION = "0.0.3"

    val flagCommands =
        mapOf(
            "none" to ViewerCommand.UNSET_FLAG,
            "red" to ViewerCommand.TOGGLE_FLAG_RED,
            "orange" to ViewerCommand.TOGGLE_FLAG_ORANGE,
            "green" to ViewerCommand.TOGGLE_FLAG_GREEN,
            "blue" to ViewerCommand.TOGGLE_FLAG_BLUE,
            "pink" to ViewerCommand.TOGGLE_FLAG_PINK,
            "turquoise" to ViewerCommand.TOGGLE_FLAG_TURQUOISE,
            "purple" to ViewerCommand.TOGGLE_FLAG_PURPLE,
        )
}
