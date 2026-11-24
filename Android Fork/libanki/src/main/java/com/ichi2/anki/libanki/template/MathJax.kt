// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2020 Arthur Milchior <arthur@milchior.fr>

package com.ichi2.anki.libanki.template

object MathJax {
    // MathJax opening delimiters
    private val sMathJaxOpenings = arrayOf("\\(", "\\[")

    // MathJax closing delimiters
    private val sMathJaxClosings = arrayOf("\\)", "\\]")

    fun textContainsMathjax(txt: String): Boolean {
        // Do you have the first opening and then the first closing,
        // or the second opening and the second closing...?

        // This assumes that the openings and closings are the same length.
        var opening: String
        var closing: String
        for (i in sMathJaxOpenings.indices) {
            opening = sMathJaxOpenings[i]
            closing = sMathJaxClosings[i]

            // What if there are more than one thing?
            // Let's look for the first opening, and the last closing, and if they're in the right order,
            // we are good.
            val firstOpeningIndex = txt.indexOf(opening)
            val lastClosingIndex = txt.lastIndexOf(closing)
            if (firstOpeningIndex != -1 && lastClosingIndex != -1 && firstOpeningIndex < lastClosingIndex) {
                return true
            }
        }
        return false
    }
}
