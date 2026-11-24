// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.utils

import com.ichi2.testutils.common.assertThrows
import org.acra.util.IOUtils.writeStringToFile
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.core.IsEqual.equalTo
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.nio.file.FileSystems
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class FileUtilTest {
    @get:Rule
    var temporaryDirectory = TemporaryFolder()
    private var testDirectorySize: Long = 0

    @Throws(Exception::class)
    private fun createSrcFilesForTest(
        temporaryRoot: File,
        testDirName: String,
    ): File {
        val grandParentDir = File(temporaryRoot, testDirName)
        val parentDir = File(grandParentDir, "parent")
        val childDir = File(parentDir, "child")
        val childDir2 = File(parentDir, "child2")
        val grandChildDir = File(childDir, "grandChild")
        val grandChild2Dir = File(childDir2, "grandChild2")
        val files =
            listOf(
                File(grandParentDir, "file1.txt"),
                File(parentDir, "file2.txt"),
                File(childDir, "file3.txt"),
                File(childDir2, "file4.txt"),
                File(grandChildDir, "file5.txt"),
                File(grandChildDir, "file6.txt"),
            )
        grandChildDir.mkdirs()
        grandChild2Dir.mkdirs()
        files.forEachIndexed { i, file ->
            writeStringToFile(file, "File " + (i + 1) + " called " + file.name)
            this.testDirectorySize += file.length()
        }
        return grandParentDir
    }

    @Test
    @Throws(Exception::class)
    fun listFilesTest() {
        // Create temporary root directory for holding test directories
        val temporaryRootDir = temporaryDirectory.newFolder("tempRootDir")

        // Create valid input
        val testDir = createSrcFilesForTest(temporaryRootDir, "testDir")
        val expectedChildren = ArrayList<File>()
        expectedChildren.add(File(testDir, "parent"))
        expectedChildren.add(File(testDir, "file1.txt"))

        val testDirChildren = FileUtil.listFiles(testDir)

        // Check that listFiles lists all files in the directory
        for (testDirChild in testDirChildren) {
            assertTrue(expectedChildren.contains(testDirChild))
        }
        assertEquals(expectedChildren.size.toLong(), testDirChildren.size.toLong())

        // Create invalid input
        assertThrows<IOException> { FileUtil.listFiles(File(testDir, "file1.txt")) }
    }

    @Test
    fun testFileNameEmpty() {
        assertThat(FileNameAndExtension.fromString(""), nullValue())
    }

    @Test
    fun testFileNameNoDot() {
        assertThat(FileNameAndExtension.fromString("abc"), nullValue())
    }

    @Test
    fun testFileNameNormal() {
        val (fileName, extension) = getValidFileNameAndExtension("abc.jpg")
        assertThat(fileName, equalTo("abc"))
        assertThat(extension, equalTo(".jpg"))
    }

    @Test
    fun testFileNameTwoDot() {
        val (fileName, extension) = getValidFileNameAndExtension("a.b.c")
        assertThat(fileName, equalTo("a.b"))
        assertThat(extension, equalTo(".c"))
    }

    @Test
    fun `test create temp file - dot`() {
        val fileNameAndExtension = getValidFileNameAndExtension("a.b.c")
        File.createTempFile(fileNameAndExtension.fileName, fileNameAndExtension.extensionWithDot)
    }

    @Test
    fun `test create temp file - too short`() {
        // createTempFile fails with a 2-character filename
        val invalidTempFile = getValidFileNameAndExtension("ok.computer")
        assertThrows<IllegalArgumentException> {
            File.createTempFile(invalidTempFile.fileName, invalidTempFile.extensionWithDot)
        }
        val valid = invalidTempFile.renameForCreateTempFile()
        File.createTempFile(valid.fileName, valid.extensionWithDot)
    }

    @Test
    fun `string representation is unchanged`() {
        val underTest = getValidFileNameAndExtension("file.ext")
        assertThat("toString()", underTest.toString(), equalTo("file.ext"))
    }

    @Test
    fun `extension replacement dot handling`() {
        // I felt that requiring the '.' prefix was unintuitive and would lead to errors
        // so both the missing and valid cases are handled

        val underTest = getValidFileNameAndExtension("file.ext")

        assertThat("replace extension, no .", underTest.replaceExtension(extension = "file").toString(), equalTo("file.file"))
        assertThat("replace extension, with .", underTest.replaceExtension(extension = ".file").toString(), equalTo("file.file"))
    }

    /**
     * Allows destructuring of a [FileNameAndExtension]
     *
     * ```kotlin
     * val (fileName, extension) = getValidFileNameAndExtension("a.b.c")
     * ```
     *
     * Destructuring doesn't work on a `FileNameAndExtension?`
     * */
    private fun getValidFileNameAndExtension(input: String) = assertNotNull(FileNameAndExtension.fromString(input))

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe - valid child file returns File`() {
        val parentDir = temporaryDirectory.newFolder("parent")
        val result = parentDir.withFileNameSafe("child.txt")
        assertEquals(File(parentDir, "child.txt"), result)
    }

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe - nested valid child returns File`() {
        val parentDir = temporaryDirectory.newFolder("parent")
        val result = parentDir.withFileNameSafe("subdir/nested/file.txt")
        assertEquals(File(parentDir, "subdir/nested/file.txt"), result)
    }

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe path traversal with dotdot throws SecurityException`() {
        val parentDir = temporaryDirectory.newFolder("parent")
        try {
            parentDir.withFileNameSafe("../outside.txt")
            fail("Expected SecurityException for path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("traversal"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe double dotdot traversal throws SecurityException`() {
        val parentDir = temporaryDirectory.newFolder("parent")
        try {
            parentDir.withFileNameSafe("../foo/../../outside.txt")
            fail("Expected SecurityException for path traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("traversal"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe child named same as parent in different location throws SecurityException`() {
        val parentDir = temporaryDirectory.newFolder("parent")
        val siblingDir = temporaryDirectory.newFolder("sibling_parent")
        // Create a file at sibling_dir/parent to test the boundary case
        File(siblingDir, "parent").mkdirs()
        try {
            parentDir.withFileNameSafe("../sibling_parent/parent/file.txt")
            fail("Expected SecurityException for same-name traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("traversal"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe - file named like parent but not traversing stays in place`() {
        val parentDir = temporaryDirectory.newFolder("parent")
        // A file literally named "parent" inside the directory should be valid
        val result = parentDir.withFileNameSafe("parent")
        assertEquals(File(parentDir, "parent"), result)
    }

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe sibling prefix parentfoo is rejected`() {
        val root = temporaryDirectory.root
        val parentDir = File(root, "parent").apply { mkdirs() }
        File(root, "parentfoo").mkdirs()
        try {
            parentDir.withFileNameSafe("../parentfoo/evil.txt")
            fail("Expected SecurityException for sibling-prefix traversal")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("traversal"))
        }
    }

    @Test
    @Throws(Exception::class)
    fun `withFileNameSafe symlink escape throws SecurityException`() {
        assumeTrue(
            "Symlink test requires POSIX file attributes",
            FileSystems.getDefault().supportedFileAttributeViews().contains("posix"),
        )
        val parentDir = temporaryDirectory.newFolder("symlinkParent")
        val outsideDir = temporaryDirectory.newFolder("symlinkOutside")
        val target = File(outsideDir, "secret.txt").apply { writeText("secret") }
        val link = File(parentDir, "link").toPath()
        try {
            Files.createSymbolicLink(link, target.toPath())
        } catch (e: UnsupportedOperationException) {
            assumeTrue("Could not create symlink: ${e.message}", false)
        } catch (e: IOException) {
            assumeTrue("Could not create symlink: ${e.message}", false)
        }
        try {
            parentDir.withFileNameSafe("link")
            fail("Expected SecurityException for symlink escape")
        } catch (e: SecurityException) {
            assertTrue(e.message!!.contains("traversal"))
        }
    }
}
