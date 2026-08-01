/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  This file incorporates code under the following license
 *  https://github.com/ankitects/anki/blob/33a923797afc9655c3b4f79847e1705a1f998d03/pylib/anki/browser.py
 *
 *    Copyright: Ankitects Pty Ltd and contributors
 *    License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html
 */

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.utils.LibAnkiAlias

object BrowserConfig {
    const val ACTIVE_CARD_COLUMNS_KEY = "activeCols"
    const val ACTIVE_NOTE_COLUMNS_KEY = "activeNoteCols"
    const val CARDS_SORT_COLUMN_KEY = "sortType"
    const val NOTES_SORT_COLUMN_KEY = "noteSortType"
    const val CARDS_SORT_BACKWARDS_KEY = "sortBackwards"
    const val NOTES_SORT_BACKWARDS_KEY = "browserNoteSortBackwards"

    @LibAnkiAlias("active_columns_key")
    fun activeColumnsKey(isNotesMode: Boolean): String = if (isNotesMode) ACTIVE_NOTE_COLUMNS_KEY else ACTIVE_CARD_COLUMNS_KEY

    @LibAnkiAlias("sort_column_key")
    fun sortColumnKey(isNotesMode: Boolean): String = if (isNotesMode) NOTES_SORT_COLUMN_KEY else CARDS_SORT_COLUMN_KEY

    @LibAnkiAlias("sort_backwards_key")
    fun sortBackwardsKey(isNotesMode: Boolean): String = if (isNotesMode) NOTES_SORT_BACKWARDS_KEY else CARDS_SORT_BACKWARDS_KEY
}

object BrowserDefaults {
    @LibAnkiAlias("CARD_COLUMNS")
    val CARD_COLUMNS: List<String> = listOf("noteFld", "template", "cardDue", "deck")

    @LibAnkiAlias("NOTE_COLUMNS")
    val NOTE_COLUMNS: List<String> = listOf("noteFld", "note", "template", "noteTags")
}
