// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.common.utils

import com.ichi2.anki.common.utils.annotation.KotlinCleanup

object HashUtil {
    /**
     * @param size Number of elements expected in the hash structure
     * @return Initial capacity for the hash structure. Copied from HashMap code
     */
    private fun capacity(size: Int): Int = ((size / .75f).toInt() + 1).coerceAtLeast(16)

    fun <T> hashSetInit(size: Int): HashSet<T> = HashSet(capacity(size))

    @KotlinCleanup("return mutableMap")
    fun <T, U> hashMapInit(size: Int): HashMap<T, U> = HashMap(capacity(size))
}
