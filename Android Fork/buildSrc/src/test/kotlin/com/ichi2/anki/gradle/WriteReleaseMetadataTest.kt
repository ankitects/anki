// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import groovy.json.JsonSlurper
import org.gradle.testfixtures.ProjectBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource
import java.io.File

/** Tests for [WriteReleaseMetadata] */
class WriteReleaseMetadataTest {
    @TempDir
    lateinit var projectDir: File

    @ParameterizedTest
    @CsvSource(
        "2.26.0dev, 22600000, dev",
        "2.26.0alpha0, 22600100, alpha",
        "2.26.0alpha99, 22600199, alpha",
        "2.26.0beta2, 22600202, beta",
        "2.26.0, 22600300, public",
    )
    fun `writes the configured version and release type as JSON`(
        name: String,
        code: Int,
        releaseType: String,
    ) {
        assertEquals(
            mapOf("versionName" to name, "versionCode" to code, "releaseType" to releaseType),
            writeMetadata(name, code),
        )
    }

    @Test
    fun `rejects unknown release types before writing metadata`() {
        assertRejectsVersionCode(22600400, IllegalStateException::class.java)
    }

    @ParameterizedTest
    @ValueSource(ints = [0, -1])
    fun `rejects nonpositive version codes`(code: Int) {
        assertRejectsVersionCode(code, IllegalArgumentException::class.java)
    }

    private fun writeMetadata(
        name: String,
        code: Int,
    ): Map<*, *> {
        val task = metadataTask(name, code)
        task.writeMetadata()
        return JsonSlurper().parse(task.outputFile.get().asFile) as Map<*, *>
    }

    private fun assertRejectsVersionCode(
        code: Int,
        exceptionType: Class<out RuntimeException>,
    ) {
        val task = metadataTask("2.26.0dev", code)
        val destination = task.outputFile.get().asFile

        assertThrows(exceptionType) { task.writeMetadata() }
        assertFalse(destination.exists())
    }

    private fun metadataTask(
        name: String,
        code: Int,
    ): WriteReleaseMetadata {
        val project = ProjectBuilder.builder().withProjectDir(projectDir).build()
        val task = project.tasks.register("writeReleaseMetadata", WriteReleaseMetadata::class.java).get()
        return task.apply {
            versionName.set(name)
            versionCode.set(code)
        }
    }
}
