// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>

package com.ichi2.anki.utils.ext

import net.ankiweb.rsdroid.Translations

/**
 * Same as [Translations.cardStatsNoCard] but removes unwanted characters like parentheses and dots
 * from the returned string:
 *
 *  Original string: "(No card to display.)" -> Returned: "No card to display"
 */
fun Translations.cardStatsNoCardClean(): String {
    // regex removes any parentheses or dots from the string
    return cardStatsNoCard().replace("[().]".toRegex(), "")
}
