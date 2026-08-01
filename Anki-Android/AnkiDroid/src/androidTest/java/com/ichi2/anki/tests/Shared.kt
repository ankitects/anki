/*
 * Copyright (c) 2014 Houssam Salem <houssam.salem.au@gmail.com>
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
package com.ichi2.anki.tests

import android.annotation.SuppressLint
import android.content.Context
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import com.ichi2.anki.backend.createDatabaseUsingRustBackend
import com.ichi2.anki.common.utils.annotation.KotlinCleanup
import com.ichi2.anki.compat.CompatHelper
import com.ichi2.anki.libanki.Collection
import com.ichi2.anki.libanki.CollectionFiles
import com.ichi2.anki.libanki.Storage
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.Assert.assertTrue
import timber.log.Timber
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/**
 * Shared methods for unit tests.
 */
@KotlinCleanup("maybe delete Shared object and make inner functions as top level")
object Shared {
    @Throws(IOException::class)
    fun getEmptyCol(): Collection {
        val f = File.createTempFile("test", ".anki2")
        // Provide a string instead of an actual File. Storage.Collection won't populate the DB
        // if the file already exists (it assumes it's an existing DB).
        val path = f.absolutePath
        val folder = path.substringBeforeLast("/")
        val name = path.substringAfterLast("/").removeSuffix(".anki2")
        assertTrue(f.delete())
        return Storage.collection(
            collectionFiles = CollectionFiles.FolderBasedCollection(folderPath = File(folder), collectionName = name),
            databaseBuilder = { backend -> createDatabaseUsingRustBackend(backend) },
        )
    }

    /**
     * @return A File object pointing to a directory in which temporary test files can be placed. The directory is
     * emptied on every invocation of this method so it is suitable to use at the start of each test.
     * Only add files (and not subdirectories) to this directory.
     */
    fun getTestDir(context: Context): File = getTestDir(context, "")

    /**
     * @param name An additional suffix to ensure the test directory is only used by a particular resource.
     * @return See getTestDir.
     */
    private fun getTestDir(
        context: Context,
        name: String,
    ): File {
        val suffix =
            if (name.isNotEmpty()) {
                "-$name"
            } else {
                ""
            }
        val dir = File(context.cacheDir, "testfiles$suffix")
        if (!dir.exists()) {
            assertTrue("failed to make directory '${dir.path}'", dir.mkdir())
        }
        val files =
            dir.listFiles()
                ?: // Had this problem on an API 16 emulator after a stress test - directory existed
                // but listFiles() returned null due to EMFILE (Too many open files)
                // Don't throw here - later file accesses will provide a better exception.
                // and the directory exists, even if it's unusable.
                return dir
        for (f in files) {
            assertTrue("failed to delete '${f.path}'", f.delete())
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
    @Throws(IOException::class)
    fun getTestFile(
        context: Context,
        name: String,
    ): File {
        assertThat("folders are not yet supported", name, not(containsString("/")))
        val inputStream =
            context.classLoader.getResourceAsStream("assets/$name")
                ?: throw FileNotFoundException("Could not find test file: assets/$name")
        val dstFile = File(getTestDir(context, name), name)
        val dst = dstFile.absolutePath
        writeToFile(inputStream, dst)
        Timber.w("extracted '%s' to '%s'", name, dst)
        assertTrue("file should exist", dstFile.exists())
        return dstFile
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
                    Timber.e("IOException while writing to file, out of retries.")
                    throw e
                } else {
                    Timber.e("IOException while writing to file, retrying...")
                    try {
                        Thread.sleep(200)
                    } catch (e1: InterruptedException) {
                        Timber.w(e1)
                    }
                }
            }
        }
    }

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
            Timber.d("Creating new file... = %s", destination)
            f.createNewFile()
            @SuppressLint("DirectSystemCurrentTimeMillisUsage")
            val startTimeMillis =
                System.currentTimeMillis()
            val sizeBytes = CompatHelper.compat.copyFile(source, destination)

            @SuppressLint("DirectSystemCurrentTimeMillisUsage")
            val endTimeMillis =
                System.currentTimeMillis()
            Timber.d("Finished writeToFile!")
            val durationSeconds = (endTimeMillis - startTimeMillis) / 1000
            val sizeKb = sizeBytes / 1024
            var speedKbSec: Long = 0
            if (endTimeMillis != startTimeMillis) {
                speedKbSec = sizeKb * 1000 / (endTimeMillis - startTimeMillis)
            }
            Timber.d(
                "Utils.writeToFile: Size: %d Kb, Duration: %d s, Speed: %d Kb/s",
                sizeKb,
                durationSeconds,
                speedKbSec,
            )
        } catch (e: IOException) {
            throw IOException(f.name + ": " + e.localizedMessage, e)
        }
    }
}
