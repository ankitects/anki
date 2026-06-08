/*
 * Copyright (c) 2025 lukstbit <52494258+lukstbit@users.noreply.github.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
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
