// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Shridhar Goel <shridhar.goel@gmail.com>

package com.ichi2.compat

import com.ichi2.anki.TestUtils
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.utils.FileOperation.Companion.getFileResource
import org.junit.Assert
import org.junit.Test
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

class CompatCopyFileTest : Test21And26() {
    @Test
    @Throws(Exception::class)
    fun testCopyFileToStream() {
        val resourcePath = getFileResource("path-traversal.zip")
        val copy = File.createTempFile("testCopyFileToStream", ".zip")
        copy.deleteOnExit()
        FileOutputStream(copy.canonicalPath).use { outputStream ->
            CompatHelper.compat.copyFile(resourcePath, outputStream)
        }
        Assert.assertEquals(TestUtils.getMD5(resourcePath), TestUtils.getMD5(copy.canonicalPath))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyStreamToFile() {
        val resourcePath = getFileResource("path-traversal.zip")
        val copy = File.createTempFile("testCopyStreamToFile", ".zip")
        copy.deleteOnExit()
        CompatHelper.compat.copyFile(resourcePath, copy.canonicalPath)
        Assert.assertEquals(TestUtils.getMD5(resourcePath), TestUtils.getMD5(copy.canonicalPath))
    }

    @Test
    @Throws(Exception::class)
    fun testCopyErrors() {
        val resourcePath = getFileResource("path-traversal.zip")
        val copy = File.createTempFile("testCopyStreamToFile", ".zip")
        copy.deleteOnExit()

        // Try copying from a bogus file
        try {
            CompatHelper.compat.copyFile(FileInputStream(""), copy.canonicalPath)
            Assert.fail("Should have caught an exception")
        } catch (e: FileNotFoundException) {
            // This is expected
        }

        // Try copying to a closed stream
        try {
            val outputStream = FileOutputStream(copy.canonicalPath).apply { close() }
            CompatHelper.compat.copyFile(resourcePath, outputStream)
            Assert.fail("Should have caught an exception")
        } catch (e: IOException) {
            // this is expected
        }

        // Try copying from a closed stream
        try {
            val source = URL(resourcePath).openStream().apply { close() }
            CompatHelper.compat.copyFile(source, copy.canonicalPath)
            Assert.fail("Should have caught an exception")
        } catch (e: IOException) {
            // this is expected
        }

        // Try copying to a bogus file
        try {
            CompatHelper.compat.copyFile(resourcePath, "")
            Assert.fail("Should have caught an exception")
        } catch (e: Exception) {
            // this is expected
        }
    }
}
