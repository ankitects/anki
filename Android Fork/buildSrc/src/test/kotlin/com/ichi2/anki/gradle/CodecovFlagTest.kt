// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CodecovFlagTest {
    @Test
    fun `maps module paths to valid unit test flags`() {
        assertEquals("a-test", CodecovFlag(":a").name)
        assertEquals("module.name_child-1-test", CodecovFlag(":module.name:child-1").name)
        val longestModuleName = "a".repeat(40)
        assertEquals("$longestModuleName-test", CodecovFlag(":$longestModuleName").name)
    }

    @Test
    fun `rejects flag names Codecov does not accept`() {
        // Codecov rejects overlong flags and unsupported characters. Enforce this at the
        // type boundary so no caller can accidentally prepare an invalid upload.
        for (name in listOf("a".repeat(41), "module+test", "module test", "module/test")) {
            val error = assertThrows(IllegalArgumentException::class.java) { CodecovFlag(":$name") }
            assertTrue(error.message.orEmpty().contains("'$name-test'"))
        }
    }

    @Test
    fun `rejects module paths that conflict with AnkiDroid coverage reports`() {
        // A literal underscore can duplicate a nested path's flag. A leading dot produces
        // a hidden XML file that the uploader's glob skips. Both flags fit Codecov's syntax.
        for (path in listOf(":nested_core", ":.hidden")) {
            val error = assertThrows(IllegalStateException::class.java) { CodecovFlag(path) }
            assertTrue(error.message.orEmpty().contains("Invalid AnkiDroid module path '$path'"))
        }
    }
}
