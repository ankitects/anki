// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.misc

import org.junit.Ignore
import org.junit.Test
import java.io.File
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/** Keep the scanner's smoke selection valid as test classes are renamed or removed. */
class OssScannerSmokeTestsTest {
    @Test
    fun `Dockerfile selects existing enabled test classes`() {
        // Unit tests run in the AnkiDroid module directory.
        val dockerfile = File("../.oss-scanner/Dockerfile").readText()
        val selectedClasses =
            Regex("""--tests(?:\s+|=)(\S+)""")
                .findAll(dockerfile.lineSequence().filterNot { it.trimStart().startsWith("#") }.joinToString("\n"))
                .map { it.groupValues[1].removeSurrounding("'").removeSurrounding("\"") }
                .toList()

        assertTrue(selectedClasses.isNotEmpty(), "No smoke tests selected in .oss-scanner/Dockerfile")
        for (className in selectedClasses) {
            assertTrue(
                Regex("""[\w$]+(?:\.[\w$]+)+""").matches(className),
                "Use an exact test class in .oss-scanner/Dockerfile: $className",
            )
            val testClass =
                try {
                    Class.forName(className, false, javaClass.classLoader)
                } catch (_: ClassNotFoundException) {
                    fail("Smoke test $className no longer exists; update .oss-scanner/Dockerfile")
                }
            assertFalse(testClass.isAnnotationPresent(Ignore::class.java), "Smoke test $className is ignored")
            assertTrue(
                testClass.methods.any {
                    it.isAnnotationPresent(Test::class.java) && !it.isAnnotationPresent(Ignore::class.java)
                },
                "Smoke test $className has no enabled JUnit tests",
            )
        }
    }
}
