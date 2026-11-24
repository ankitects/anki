// SPDX-License-Identifier: GPL-3.0-or-later
// SPDX-FileCopyrightText: Copyright (c) 2021 Mani infinyte01@gmail.com

package com.ichi2.anki.jsaddons

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ichi2.anki.RobolectricTest
import com.ichi2.anki.common.storage.CollectionHelper
import com.ichi2.testutils.ShadowStatFs
import com.ichi2.utils.FileOperation.Companion.getFileResource
import io.mockk.every
import io.mockk.spyk
import junit.framework.TestCase.assertTrue
import org.apache.commons.compress.archivers.ArchiveException
import org.hamcrest.CoreMatchers.not
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.io.FileMatchers.anExistingDirectory
import org.hamcrest.io.FileMatchers.anExistingFile
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException
import kotlin.test.assertFailsWith

@RunWith(AndroidJUnit4::class)
class TgzPackageExtractTest : RobolectricTest() {
    private lateinit var tarballPath: String
    private lateinit var addonDir: File
    private lateinit var addonPackage: TgzPackageExtract

    @Before
    override fun setUp() {
        super.setUp()

        val currentAnkiDroidDirectory = CollectionHelper.getCurrentAnkiDroidDirectory(targetContext)
        ShadowStatFs.markAsNonEmpty(currentAnkiDroidDirectory)
        addonPackage = TgzPackageExtract(targetContext)
        addonDir = File(currentAnkiDroidDirectory, "addons")
        tarballPath = getFileResource("valid-ankidroid-js-addon-test-1.0.0.tgz")
        if (!addonDir.exists()) {
            addonDir.mkdirs()
        }
    }

    @After
    override fun tearDown() {
        super.tearDown()
        ShadowStatFs.reset()
    }

    /**
     * Test if the file is valid GZip file
     * @throws IOException
     */
    @Test
    @Throws(IOException::class)
    fun isGzipTest() {
        // test if file is tar gzip
        assertTrue(addonPackage.isGzip(File(tarballPath)))
    }

    /**
     * Test if extracted file exists in the output folder.
     * The current test will extract in .tgz in following structure
     * tempAddonDir
     * - package
     * - index.js
     * - README.md
     * - package.json
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun extractTarGzipToAddonFolderTest() {
        // extract file to tempAddonFolder, the function first unGzip .tgz to .tar then unTar(extract) .tar file
        addonPackage.extractTarGzipToAddonFolder(File(tarballPath), addonDir)

        // test if package folder exists
        val packagePath = File(addonDir, "package")
        assertThat(packagePath, anExistingDirectory())

        // test if index.js extracted successfully
        val indexJsPath = File(packagePath, "index.js")
        assertThat(indexJsPath, anExistingFile())

        // test if README.md extracted successfully
        val readmePath = File(packagePath, "README.md")
        assertThat(readmePath, anExistingFile())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertThat(packageJsonPath, anExistingFile())
    }

    /**
     * Test if .tar file unTar successfully to temp folder
     *
     * @throws IOException
     * @throws ArchiveException
     */
    @Test
    @Throws(IOException::class, ArchiveException::class)
    fun unTarTest() {
        // first unGzip .tgz file to .tar
        val unGzipFile = addonPackage.unGzip(File(tarballPath), addonDir)

        // unTar .tar file to temp folder, it is same as extract of files to tempAddonDir
        addonPackage.unTar(unGzipFile, addonDir)

        // test if package folder exists
        val packagePath = File(addonDir, "package")
        assertThat(packagePath, anExistingDirectory())

        // test if index.js extracted successfully
        val indexJsPath = File(packagePath, "index.js")
        assertThat(indexJsPath, anExistingFile())

        // test if README.md extracted successfully
        val readmePath = File(packagePath, "README.md")
        assertThat(readmePath, anExistingFile())

        // test if package.json extracted successfully
        val packageJsonPath = File(packagePath, "package.json")
        assertThat(packageJsonPath, anExistingFile())
    }

    /**
     * Test that failure to create the addon directory is reported to the caller
     * instead of being silently swallowed
     */
    @Test
    fun extractionFailureIsReportedTest() {
        // a regular file where the addon directory should go: directory creation must fail
        val blocker = File(addonDir, "blocker").also { it.writeText("") }
        val addonsPackageDir = File(blocker, "some-addon")

        assertFailsWith<IOException> {
            addonPackage.extractTarGzipToAddonFolder(File(tarballPath), addonsPackageDir)
        }
    }

    /**
     * Test that a failed extraction is reported to the caller
     * instead of completing as if it had succeeded
     */
    @Test
    fun unTarFailureIsReportedTest() {
        val failingExtract = spyk(addonPackage)
        every { failingExtract.unTar(any(), any()) } throws IOException("simulated failure")

        assertFailsWith<IOException> {
            failingExtract.extractTarGzipToAddonFolder(File(tarballPath), addonDir)
        }
    }

    @Test
    fun failedExtractionCleansUpPartialAddonTest() {
        // an addon package dir inside the addons dir, as production lays it out
        val addonsPackageDir = File(addonDir, "some-addon")

        val failingExtract = spyk(addonPackage)
        every { failingExtract.unTar(any(), any()) } answers {
            // simulate a partial extraction before the failure
            File(addonsPackageDir, "partial.js").writeText("")
            throw IOException("simulated failure")
        }

        assertFailsWith<IOException> {
            failingExtract.extractTarGzipToAddonFolder(File(tarballPath), addonsPackageDir)
        }
        assertThat(addonsPackageDir, not(anExistingDirectory()))
    }

    /**
     * Test if .tgz file successfully unGzipped
     * i.e. .tgz changed to .tar file
     *
     * @throws IOException
     */
    @Test
    @Throws(IOException::class)
    fun unGzipTest() {
        val unGzipFile = addonPackage.unGzip(File(tarballPath), addonDir)

        // test if unGzip successfully return tar file
        assertThat(unGzipFile, anExistingFile())

        // test if .tgz file changed to .tar file
        assertTrue(File(unGzipFile.toString()).absolutePath.endsWith(".tar"))
    }
}
