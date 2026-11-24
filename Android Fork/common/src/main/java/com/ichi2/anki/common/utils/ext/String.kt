// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.common.utils.ext

/** Returns `true` if this string contains only ASCII characters or is empty. */
fun String.isAscii(): Boolean = all { it <= '\u007f' }
