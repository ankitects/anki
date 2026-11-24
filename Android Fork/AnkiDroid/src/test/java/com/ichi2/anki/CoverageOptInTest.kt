// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki

import com.ichi2.anki.common.annotations.CoverageOptIn
import com.ichi2.testutils.annotatedClassNames
import org.junit.jupiter.api.Test
import java.io.File
import kotlin.test.assertEquals

/** Keeps [CoverageOptIn] annotations and `gradle/jacoco-includes.txt` in sync. */
class CoverageOptInTest {
    @Test
    fun `explicit JaCoCo includes match CoverageOptIn annotations`() {
        val root = repositoryRoot()
        val includes = File(root, "gradle/jacoco-includes.txt").readLines().filter { it.isNotBlank() }
        val packagePrefixes = includes.filter { it.endsWith(".*") }.map { it.removeSuffix("*") }
        val expectedIncludes =
            javaClass.classLoader
                .annotatedClassNames(CoverageOptIn::class.java, root)
                .filter { name -> packagePrefixes.none(name::startsWith) }
                .flatMap { listOf(it, "$it$*") }
                .toSet()

        assertEquals(
            expectedIncludes,
            includes.filterNot { it.endsWith(".*") }.toSet(),
            "Update gradle/jacoco-includes.txt when adding or removing @CoverageOptIn, including nested-class patterns.",
        )
    }

    private fun repositoryRoot(): File =
        generateSequence(File(".").canonicalFile) { it.parentFile }
            .first { File(it, "gradle/jacoco-includes.txt").isFile }
}
