// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.common.json

interface NamedObject {
    val name: String
}

class NamedJSONComparator : Comparator<NamedObject> {
    override fun compare(
        lhs: NamedObject,
        rhs: NamedObject,
    ): Int {
        val o1 = lhs.name
        val o2 = rhs.name
        return o1.compareTo(o2, ignoreCase = true)
    }

    companion object {
        val INSTANCE = NamedJSONComparator()
    }
}
