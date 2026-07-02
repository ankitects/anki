/*
 *  Copyright (c) 2023 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

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
