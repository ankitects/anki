// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class VersionCodeTest {
    @Test
    fun `accepts Groovy Kotlin whitespace and comment formats`() {
        for (script in listOf(
            "versionCode 22500202", // Groovy
            "versionCode = 22500202", // Kotlin
            "        versionCode=\n            22500202;\n", // Whitespace
            "versionCode = 22500202 // beta", // Comment
        )) {
            assertEquals(22500202, VersionCode.read(script))
        }
    }

    @Test
    fun `ignores task property setters when reading the app version code`() {
        val script =
            """
            android {
                defaultConfig {
                    versionCode = 22600100
                }
            }
            tasks.register('writeReleaseMetadata', WriteReleaseMetadata) {
                versionCode.set(android.defaultConfig.versionCode)
            }
            """.trimIndent()

        assertEquals(22600100, VersionCode.read(script))
    }

    @Test
    fun `rejects missing ambiguous and unsupported versionCode declarations`() {
        for (script in listOf(
            "",
            "versionCode = appVersionCode",
            "versionCode = 22_500_202",
            "versionCode = 22500202 + 1",
            "versionCode = 22500202.toInt()",
            "versionCode = 22500202\n    .toInt()",
            "versionCode=22500202\nversionCode=22500203",
            "versionCode=22500202\nversionCode=appVersionCode",
            "versionCode=2147483648",
        )) {
            val error = assertThrows(IllegalArgumentException::class.java) { VersionCode.read(script) }
            assertTrue(error.message!!.contains("literal"))
        }
    }

    @Test
    fun `rejects zero and leading-zero literals`() {
        // can be parsed as octal
        for (literal in listOf("0100000", "022500202", "001", "0")) {
            assertThrows(IllegalArgumentException::class.java) { VersionCode.read("versionCode = $literal") }
        }
    }

    @Test
    fun `rejects chained calls after whitespace or comments`() {
        for (gap in listOf("\n\n", "\n// adjustment\n", "\n/* adjustment\n * continues here */\n")) {
            assertThrows(IllegalArgumentException::class.java) { VersionCode.read("versionCode = 22500202$gap    .plus(1)") }
        }
    }

    @Test
    fun `ignores dots in comments following a literal`() {
        for (comment in listOf("// See example.com", "/* See example.com */")) {
            assertEquals(22500202, VersionCode.read("versionCode = 22500202\n$comment\nversionName = \"2.25.0beta2\""))
        }
    }

    @Test
    fun `accepts unchanged codes one increment and the maximum bump`() {
        VersionCode.validate(previous = 22500202, code = 22500202)
        VersionCode.validate(previous = 22500202, code = 22500203)
        VersionCode.validate(previous = 22500300, code = 22600300)
    }

    @Test
    fun `rejects oversized and decreasing codes`() {
        for (code in listOf(22600203, 22500201, Int.MAX_VALUE)) {
            assertThrows(IllegalArgumentException::class.java) { VersionCode.validate(22500202, code) }
        }
    }

    @Test
    fun `rejects a missing code`() {
        assertThrows(IllegalArgumentException::class.java) { VersionCode.validate(22500202, null) }
    }
}
