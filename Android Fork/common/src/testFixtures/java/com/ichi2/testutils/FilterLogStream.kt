// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.testutils

import java.io.OutputStream
import java.io.PrintStream
import java.util.regex.Pattern

class FilterLogStream(
    stream: OutputStream,
    private val pattern: Pattern,
) : PrintStream(stream) {
    override fun println(x: String) {
        if (!pattern.matcher(x).find()) return
        super.println(x)
    }
}

fun PrintStream.filter(regex: String): PrintStream = FilterLogStream(this, Pattern.compile(regex))
