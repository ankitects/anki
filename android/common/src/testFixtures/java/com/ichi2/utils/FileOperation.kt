// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Piyush Goel <piyushgoel2008@gmail.com>

package com.ichi2.utils

import java.io.File
import java.io.RandomAccessFile

class FileOperation {
    companion object {
        fun getFileResource(name: String): String {
            val resource = FileOperation::class.java.classLoader!!.getResource(name)
            return (File(resource.path).path)
        }

        fun getFileContentsBytes(exportedFile: File): ByteArray {
            val f = RandomAccessFile(exportedFile, "r")
            val b = ByteArray(f.length().toInt())
            f.readFully(b)
            return b
        }
    }
}
