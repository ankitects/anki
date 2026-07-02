/*
 * Copyright (c) 2020 David Allison <davidallisongithub@gmail.com>
 *
 * This program is free software; you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.ankiweb.rsdroid.ankiutil

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

open class CompatV16 : Compat {
    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: String,
    ) {
        try {
            FileInputStream(File(source)).use { fileInputStream ->
                copyFile(
                    fileInputStream,
                    target,
                )
            }
        } catch (e: IOException) {
            throw e
        }
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(
        source: String,
        target: OutputStream,
    ): Long {
        var count: Long
        try {
            FileInputStream(File(source)).use { fileInputStream ->
                count = copyFile(fileInputStream, target)
            }
        } catch (e: IOException) {
            throw e
        }
        return count
    }

    // Until API 26 do the copy using streams
    @Throws(IOException::class)
    override fun copyFile(
        source: InputStream,
        target: String,
    ): Long {
        var bytesCopied: Long
        try {
            FileOutputStream(target).use { targetStream ->
                bytesCopied = copyFile(source, targetStream)
            }
        } catch (ioe: IOException) {
            throw ioe
        }
        return bytesCopied
    }

    @Throws(IOException::class)
    private fun copyFile(
        source: InputStream,
        target: OutputStream,
    ): Long {
        // balance memory and performance, it appears 32k is the best trade-off
        // https://stackoverflow.com/questions/10143731/android-optimal-buffer-size
        val buffer = ByteArray(1024 * 32)
        var count: Long = 0
        var n: Int
        while (source.read(buffer).also { n = it } != -1) {
            target.write(buffer, 0, n)
            count += n.toLong()
        }
        target.flush()
        return count
    }
}
