/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.notetype

/**
 * The name of a card type
 *
 * Names are case-insensitive: two card types may not differ just by case.
 * A "+" suffix is added to the name if a duplicate occurs
 *
 * @see com.ichi2.anki.libanki.CardTemplate.name
 */
class CardTypeName private constructor(
    val value: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CardTypeName) return false
        return value.equals(other.value, ignoreCase = true)
    }

    override fun hashCode(): Int = value.lowercase().hashCode()

    override fun toString(): String = value

    companion object {
        fun fromString(value: String) = CardTypeName(value.trim())
    }
}
