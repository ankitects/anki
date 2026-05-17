// SPDX-License-Identifier: GPL-3.0-or-later

// Convention plugin: applies the Kotlin JVM plugin and pins Java/Kotlin
// toolchain settings for pure-JVM (non-Android) modules in this project.

import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
}

tasks.withType(JavaCompile::class).configureEach {
    sourceCompatibility = JavaVersion.VERSION_17.toString()
    targetCompatibility = JavaVersion.VERSION_17.toString()
}

tasks.withType(KotlinCompile::class).configureEach {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

// Skip self-application for `:lint-rules`, which provides the checks.
if (path != ":lint-rules") {
    pluginManager.apply("com.android.lint")
    dependencies {
        "lintChecks"(project(":lint-rules"))
    }
}
