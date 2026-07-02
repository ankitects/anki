// SPDX-License-Identifier: GPL-3.0-or-later

// Convention plugin: Android library published as a public API artifact.
//
// Distinct from ankidroid.android-library:
//   * Java source/target compat is 11 (not 17) for consumer compatibility.
//   * Kotlin explicit-API strict mode is enabled (library-author quality check).
//   * minSdk is not set.
//
// Publishing (maven-publish, singleVariant setup, POM metadata) is left to
// the consuming module — it's intrinsically per-module configuration.

import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.libsVersionFor
import org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension

plugins {
    id("com.android.library")
}

extensions.configure<LibraryExtension> {
    compileSdk = libsVersionFor("compileSdk").toInt()

    compileOptions {
        // API remains on VERSION_11 for consumer compatibility.
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

extensions.configure<KotlinAndroidProjectExtension> {
    explicitApi()
    compilerOptions {
        // Stricter checks on public API shape for library authors.
        // See https://kotlinlang.org/docs/whatsnew14.html#explicit-api-mode-for-library-authors
        freeCompilerArgs.add("-Xexplicit-api=strict")
    }
}

// Shared project-wide lint configuration.
apply(from = "${rootDir}/lint.gradle")

dependencies {
    "lintChecks"(project(":lint-rules"))
}
