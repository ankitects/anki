/*
 *  Copyright (c) 2024 David Allison <davidallisongithub@gmail.com>
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

package com.ichi2.anki.utils.ext

import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Converts an [InputStream] to a [String].
 *
 * @receiver [InputStream] to convert
 * @return [String] version of the [InputStream]
 */
fun InputStream.convertToString(): String {
    var contentOfMyInputStream = ""
    try {
        val rd = BufferedReader(InputStreamReader(this), 4096)
        var line: String?
        val sb = StringBuilder()
        while (rd.readLine().also { line = it } != null) {
            sb.append(line)
        }
        rd.close()
        contentOfMyInputStream = sb.toString()
    } catch (e: Exception) {
        Timber.w(e)
    }
    return contentOfMyInputStream
}
