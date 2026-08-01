// SPDX-License-Identifier: GPL-3.0-or-later

/*
 Convention plugin: applies `com.android.library` and pins the settings
 shared across every Android library module in this project.

 This does not apply to the API module,
 */

import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.libsVersionFor

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    compileSdk = libsVersionFor("compileSdk").toInt()

    defaultConfig {
        minSdk = libsVersionFor("minSdk").toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Shared project-wide lint configuration.
apply(from = "${rootDir}/lint.gradle")

// `:vbpd` is vendored third-party code; not subject to our lint rules.
if (path != ":vbpd") {
    dependencies {
        // PERF: some rules do not need to be applied... but the full run was 3s
        "lintChecks"(project(":lint-rules"))
    }
}

// Apply jacoco so module unit tests produce .exec files that
// AnkiDroid's jacocoUnitTestReport aggregates across modules.
apply(from = "${rootDir}/jacocoSupport.gradle")
