// SPDX-License-Identifier: GPL-3.0-or-later

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
