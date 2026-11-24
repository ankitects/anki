// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.cardviewer

/** Appends the HTML answer's keyboard attributes, including a trailing space. */
internal fun StringBuilder.appendNoSuggestAttributes() {
    append("data-ankidroid-nosuggest=\"true\"")
    append(' ')
    append("enterkeyhint=\"done\"")
    append(' ')
}
