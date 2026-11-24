// SPDX-License-Identifier: GPL-3.0-or-later

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
