// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.withType
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

/** Uses the shared include list for test instrumentation and cache invalidation. */
fun Project.configureJacocoAgent() {
    val includeFile = rootProject.layout.projectDirectory.file("gradle/jacoco-includes.txt")
    val includes =
        providers
            .fileContents(includeFile)
            .asText
            .get()
            .lines()
            .filter { it.isNotBlank() }

    tasks.withType<Test>().configureEach {
        inputs.file(includeFile).withPropertyName("coverageIncludes")
        val jacoco = extensions.getByType(JacocoTaskExtension::class.java)
        jacoco.isIncludeNoLocationClasses = true
        jacoco.includes = includes
        jacoco.excludes = listOf("jdk.internal.*")
    }
}
