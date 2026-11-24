// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import java.io.File
import java.nio.file.Files

/**
 * Reuse the test fixtures so CI warms the same dependencies and isolated cache as the tests.
 *
 * Run from the repository root: `./gradlew -p buildSrc downloadTestKitDependencies`.
 */
fun main() {
    withTemporaryProject("coverage") { directory ->
        JacocoPluginTest().apply {
            projectDir = directory
            downloadDependencies()
        }
    }
    // Also covers the backend fixture's JUnit dependencies.
    withTemporaryProject("screenshots") { directory ->
        ScreenshotPluginTest().apply {
            projectDir = directory
            downloadDependencies()
        }
    }
}

private fun withTemporaryProject(
    fixtureName: String,
    action: (File) -> Unit,
) {
    println("Warming $fixtureName TestKit dependencies")
    val directory = Files.createTempDirectory("test-kit-dependencies-$fixtureName-").toFile()
    try {
        action(directory)
    } finally {
        directory.deleteRecursively()
    }
}
