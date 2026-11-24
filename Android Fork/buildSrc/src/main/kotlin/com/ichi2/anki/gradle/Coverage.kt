// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import java.util.Properties

/** Uses the shared Gradle preference, falling back to the checkout's legacy setting. */
fun Project.isCoverageEnabled(): Boolean {
    val gradleProperty = providers.gradleProperty("enable_coverage").orNull
    if (gradleProperty != null) return gradleProperty != "false"

    val localProperties = Properties()
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { localProperties.load(it) }
    }
    return localProperties.getProperty("enable_coverage") != "false"
}
