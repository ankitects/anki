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

import android.content.Context
import android.text.TextUtils
import androidx.annotation.CheckResult
import org.junit.Assert
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

object Shared {
    /**
     * Utility method to write to a file.
     * Throws the exception, so we can report it in syncing log
     */
    @Throws(IOException::class)
    private fun writeToFileImpl(
        source: InputStream,
        destination: String,
    ) {
        val f = File(destination)
        try {
            f.createNewFile()
            getCompat().copyFile(source, destination)
        } catch (e: IOException) {
            throw IOException(f.name + ": " + e.localizedMessage, e)
        }
    }

    /**
     * Calls [.writeToFileImpl] and handles IOExceptions
     * Does not close the provided stream
     * @throws IOException Rethrows exception after a set number of retries
     */
    @Throws(IOException::class)
    fun writeToFile(
        source: InputStream,
        destination: String,
    ) {
        // sometimes this fails and works on retries (hardware issue?)
        val retries = 5
        var retryCnt = 0
        var success = false
        while (!success && retryCnt++ < retries) {
            try {
                writeToFileImpl(source, destination)
                success = true
            } catch (e: IOException) {
                if (retryCnt == retries) {
                    throw e
                } else {
                    try {
                        Thread.sleep(200)
                    } catch (e1: InterruptedException) {
                        e1.printStackTrace()
                    }
                }
            }
        }
    }

    /**
     * @param name An additional suffix to ensure the test directory is only used by a particular resource.
     * @return See getTestDir.
     */
    private fun getTestDir(
        context: Context,
        name: String,
    ): File {
        var suffix = ""
        if (!TextUtils.isEmpty(name)) {
            suffix = "-$name"
        }
        val dir = File(context.cacheDir, "testfiles$suffix")
        if (!dir.exists()) {
            Assert.assertTrue(dir.mkdir())
        }
        val files =
            dir.listFiles()
                ?: // Had this problem on an API 16 emulator after a stress test - directory existed
// but listFiles() returned null due to EMFILE (Too many open files)
// Don't throw here - later file accesses will provide a better exception.
// and the directory exists, even if it's unusable.
                return dir
        for (f in files) {
            Assert.assertTrue(f.delete())
        }
        return dir
    }

    /**
     * Copy a file from the application's assets directory and return the absolute path of that
     * copy.
     *
     * Files located inside the application's assets collection are not stored on the file
     * system and can not return a usable path, so copying them to disk is a requirement.
     */
    @JvmStatic
    @CheckResult
    @Throws(IOException::class)
    fun getTestFilePath(
        context: Context,
        name: String,
    ): String {
        val `is` =
            context.classLoader.getResourceAsStream("assets/$name")
                ?: throw FileNotFoundException("Could not find test file: assets/$name")
        val dst = File(getTestDir(context, name), name).absolutePath
        writeToFile(`is`, dst)
        return dst
    }
}
