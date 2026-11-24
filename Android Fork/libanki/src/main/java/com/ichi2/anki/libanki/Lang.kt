// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.libanki

import com.ichi2.anki.libanki.utils.LibAnkiAlias

/**
 * strip off unicode isolation markers from a translated string for testing purposes
 */
@LibAnkiAlias("without_unicode_isolation")
fun withoutUnicodeIsolation(s: String): String = s.replace("\u2068", "").replace("\u2069", "")

@LibAnkiAlias("with_collapsed_whitespace")
fun withCollapsedWhitespace(s: String): String = s.replace("\\s+", " ")
