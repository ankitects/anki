// SPDX-License-Identifier: GPL-3.0-or-later

/*
 Convention plugin: applies `com.android.library` and pins the settings
 shared across every Android library module in this project.

 This does not apply to the API module,
 */

import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.configureAndroidLint
import com.ichi2.anki.gradle.configureJacocoAgent
import com.ichi2.anki.gradle.libsVersionFor
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension

plugins {
    id("com.android.library")
    jacoco
}

extensions.configure<LibraryExtension> {
    compileSdk = libsVersionFor("compileSdk").toInt()
    compileSdkMinor = libsVersionFor("compileSdkMinor").toInt()

    defaultConfig {
        minSdk = libsVersionFor("minSdk").toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Shared project-wide lint configuration.
configureAndroidLint()

// `:vbpd` is vendored third-party code; not subject to our lint rules.
if (path != ":vbpd") {
    dependencies {
        // PERF: some rules do not need to be applied... but the full run was 3s
        "lintChecks"(project(":lint-rules"))
    }
}

// Configure jacoco so module unit tests produce .exec files that
// AnkiDroid's jacocoUnitTestReport aggregates across modules.
val jacocoToolVersion = libsVersionFor("jacoco")

extensions.configure<JacocoPluginExtension> {
    toolVersion = jacocoToolVersion
}
// Robolectric loads classes without a code location; record hits on them in each suite's report.
configureJacocoAgent()

extensions.configure<LibraryExtension> {
    testCoverage.jacocoVersion = jacocoToolVersion
}
