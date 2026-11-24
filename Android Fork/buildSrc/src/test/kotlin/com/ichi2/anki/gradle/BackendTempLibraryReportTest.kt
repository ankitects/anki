// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.DisabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class BackendTempLibraryReportTest {
    @TempDir
    lateinit var directory: File

    private val checksum = "a".repeat(40)

    @Test
    fun `counts only originals and numbered copies on supported platforms without deleting anything`() {
        val included =
            listOf(
                "librsdroid-$checksum.so",
                "librsdroid-$checksum-1.so",
                "librsdroid-$checksum.dylib",
                "librsdroid-$checksum-12.dylib",
                "rsdroid-$checksum.dll",
                "rsdroid-$checksum-5.dll",
            )
        val excluded =
            listOf(
                "librsdroid-$checksum.so.lock",
                "librsdroid-$checksum-123.tmp",
                "librsdroid-not-a-checksum.so",
                "librsdroid-$checksum-0.so",
                "librsdroid-$checksum.dll",
                "rsdroid-$checksum.so",
            )
        (included + excluded).forEach { File(directory, it).writeText("x") }
        File(directory, "librsdroid-${"b".repeat(40)}.so").mkdir()

        val report = checkNotNull(backendTempLibraryReport(setOf(directory), 5))
        assertTrue(report.contains("(6 files)"), report)
        included.forEach { assertTrue(report.contains(File(directory, it).path), report) }
        excluded.forEach { assertFalse(report.contains(File(directory, it).path), report) }
        assertNull(backendTempLibraryReport(setOf(directory), 6))
        val remaining =
            directory
                .listFiles()!!
                .filter { it.isFile }
                .map { it.name }
                .toSet()
        assertEquals((included + excluded).toSet(), remaining)
    }

    @Test
    fun `sums bytes across directories and respects the threshold exactly`() {
        val otherDirectory = File(directory, "other").apply { mkdir() }
        val size = 1024L * 1024
        listOf(directory, otherDirectory).forEach {
            RandomAccessFile(File(it, "librsdroid-$checksum.so"), "rw").use { file -> file.setLength(size) }
        }
        val directories = setOf(directory, otherDirectory, File(directory, "missing"))
        assertNull(backendTempLibraryReport(directories, 2 * size))
        val report = checkNotNull(backendTempLibraryReport(directories, 2 * size - 1))
        assertTrue(report.contains("2.0 MiB (2 files)"), report)
    }

    @Test
    @DisabledOnOs(OS.WINDOWS, disabledReason = "Creating symlinks requires special Windows privileges")
    fun `ignores symbolic links to libraries`() {
        val target = File(directory, "target").apply { writeText("library") }
        Files.createSymbolicLink(File(directory, "librsdroid-$checksum.so").toPath(), target.toPath())
        assertNull(backendTempLibraryReport(setOf(directory), 0))
        assertTrue(target.exists())
    }

    @Test
    fun `empty or removed directories do not produce a warning`() {
        assertNull(backendTempLibraryReport(setOf(directory, File(directory, "missing")), 0))
    }
}
