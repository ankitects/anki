// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.model

/**
 * The name of a Note Type's field
 *
 * example: `Front`
 */
@JvmInline
value class FieldName(
    val name: String,
) {
    override fun toString() = name
}
