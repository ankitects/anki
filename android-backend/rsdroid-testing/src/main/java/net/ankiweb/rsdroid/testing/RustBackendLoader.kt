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
package net.ankiweb.rsdroid.testing

import org.apache.commons.exec.OS
import java.io.*
import java.lang.IllegalStateException
import java.lang.RuntimeException
import java.security.MessageDigest
import java.util.HashMap
import kotlin.Throws

/**
 * Loads a librsdroid.so alternative to allow testing of rsdroid under a Robolectric-based environment
 */
object RustBackendLoader {
    private var hasSetUp = false
    private val FILENAME_TO_PATH_CACHE = HashMap<String, String>()
    var PRINT_DEBUG = false

    /**
     * Allows unit testing rsdroid under Robolectric <br></br>
     * Loads (via [Runtime.load]) a librsdroid.so alternative compiled for the current operating system.<br></br><br></br>
     *
     * This call is cached and is a no-op if called multiple times.
     *
     * Note the @Synchronized label is misleading - see the docs for loadPath()
     *
     * @throws IllegalStateException OS is not Windows, Linux or macOS
     * @throws RuntimeException Failure when extracting library to load
     * @throws UnsatisfiedLinkError The library could not be loaded
     */
    @JvmStatic
    @Synchronized
    fun ensureSetup() {
        if (hasSetUp) {
            return
        }
        // This should help diagnose some issues,
        print("loading rsdroid-testing for: " + System.getProperty("os.name"))
        if (OS.isFamilyWindows()) {
            load("rsdroid", ".dll")
        } else if (OS.isFamilyMac()) {
            load("librsdroid", ".dylib")
        } else if (OS.isFamilyUnix()) {
            load("librsdroid", ".so")
        } else {
            val osName = System.getProperty("os.name")
            throw IllegalStateException(String.format("Could not determine OS Type for: '%s'", osName))
        }
        hasSetUp = true
    }

    private fun print(message: String) {
        if (PRINT_DEBUG) {
            println(message)
        }
    }

    /**
     * loads a named file in the jar via [Runtime.load]
     *
     * @param fileName The name of the file in the jar
     * @param extension The extension of the file in the jar
     *
     * @throws UnsatisfiedLinkError The library could not be loaded
     * @throws RuntimeException Failure when extracting library to load
     */
    private fun load(
        fileName: String,
        extension: String,
    ) {
        val path = getPathFromResourceStream(fileName, extension)
        loadPath(path)
    }

    /**
     * Subtle behaviour alert: while the routine that calls this is protected with a
     * @Synchronized attribution, the lock it uses is based on the classloader that is
     * active at the time. JUnit and Robolectric will alter the classloader for different tests
     * (eg some do not use Robolectric's classloader at all, and other tests like BindingAndroidTest
     * will use multiple classloader instances due to the use of @Config). This means this code
     * is not guaranteed to execute only once, and after the first invocation, an "already loaded"
     * error will be thrown by Java, which we have to swallow.
     */
    private fun loadPath(path: String) {
        try {
            Runtime.getRuntime().load(path)
        } catch (e: UnsatisfiedLinkError) {
            if (!File(path).exists()) {
                val exception =
                    FileNotFoundException("Extracted file was not found. Maybe the temp folder was deleted. Please try again: '$path'")
                throw RuntimeException(exception)
            }
            if (e.message == null || !e.message!!.contains("already loaded in another classloader")) {
                throw e
            } else {
                // native library loaded by a different classloader in the same process
            }
        }
    }

    /**
     * Extracts a named file from a JAR and saves it to a temp folder
     *
     * @param fileName The name of the file in the jar
     * @param extension The extension of the file in the jar
     * @return A path (on disk) to the extracted file from the JAR
     * @throws IllegalStateException The named file did not exist in the jar.
     * @throws IOException Error copying the file to the filesystem
     */
    @Throws(IOException::class)
    private fun getPathFromResourceStream(
        fileName: String,
        extension: String,
    ): String {
        // TODO: Ensure that this is reasonably handled without too much copying.
        // Note: this will leave some data in the temp folder.
        val fullFilename = fileName + extension

        // maintain a cache to the files so we reduce IO activity if a file has already been extracted.
        if (FILENAME_TO_PATH_CACHE.containsKey(fullFilename)) {
            return FILENAME_TO_PATH_CACHE[fullFilename]!!
        }

        val buffer = ByteArray(8 * 1024)
        val checksum =
            withStream(fullFilename) { stream ->
                val digest = MessageDigest.getInstance("SHA-1")
                var bytesRead: Int
                while (stream.read(buffer).also { bytesRead = it } != -1) {
                    digest.update(buffer, 0, bytesRead)
                }
                digest.digest().joinToString("") { "%02x".format(it) }
            }
        val expectedFile = File(System.getProperty("java.io.tmpdir"), "$fileName-$checksum$extension")
        if (!expectedFile.exists()) {
            val tempFile = File.createTempFile(fileName, extension)
            tempFile.outputStream().use { outStream ->
                withStream(fullFilename) { inStream ->
                    var bytesRead: Int
                    while (inStream.read(buffer).also { bytesRead = it } != -1) {
                        outStream.write(buffer, 0, bytesRead)
                    }
                }
                outStream.flush()
                outStream.close()
            }
            tempFile.renameTo(expectedFile)
        }
        FILENAME_TO_PATH_CACHE[fullFilename] = expectedFile.path
        return expectedFile.absolutePath
    }

    private fun <T> withStream(
        fullFilename: String,
        func: (InputStream) -> T,
    ): T = func(RustBackendLoader::class.java.classLoader!!.getResourceAsStream(fullFilename))
}
