/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
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
 */

package com.ichi2.anki.model

/**
 * Allows filtering a search by the state of cards
 *
 * @see [anki.search.SearchNode.CardState]
 */
enum class CardStateFilter {
    ALL_CARDS,
    NEW,
    DUE,
    ;

    val toSearch: String
        get() =
            when (this) {
                ALL_CARDS -> ""
                NEW -> "is:new "
                DUE -> "is:due "
            }
}
