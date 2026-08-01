// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2018 Mike Hardy <mike@mikehardy.net>

package com.ichi2.anki

import com.ichi2.utils.FileOperation.Companion.getFileContentsBytes
import java.io.File
import java.security.MessageDigest

open class TestUtils {
    /** get the MD5 checksum (in hex) for the given filename  */
    companion object {
        @Throws(Exception::class)
        fun getMD5(filename: String): String {
            val md = MessageDigest.getInstance("MD5")
            md.update(getFileContentsBytes(File(filename)))
            val hex = StringBuilder()
            for (b in md.digest()) {
                hex.append(String.format("%02x", b))
            }
            return hex.toString()
        }
    }
}
