// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki.utils

/**
 * Specifies the name of the method
 * in anki's [pylib](https://github.com/ankitects/anki/tree/main/pylib/anki)
 */
@Retention(AnnotationRetention.SOURCE)
internal annotation class LibAnkiAlias(
    @Suppress("unused") val alias: String,
)
