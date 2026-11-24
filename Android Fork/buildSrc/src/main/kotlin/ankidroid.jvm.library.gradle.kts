// SPDX-License-Identifier: GPL-3.0-or-later

// Convention plugin: applies the Kotlin JVM plugin and pins Java/Kotlin
// toolchain settings for pure-JVM (non-Android) modules in this project.

import com.ichi2.anki.gradle.libsLibrary
import com.ichi2.anki.gradle.libsVersionFor
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.jetbrains.kotlin.jvm")
    jacoco
}

configure<JacocoPluginExtension> {
    toolVersion = libsVersionFor("jacoco")
}

dependencies {
    "testRuntimeOnly"(libsLibrary("junit-platform-launcher"))
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
    configure<com.android.build.api.dsl.Lint> {
        lintConfig = rootProject.file("lint-release.xml")

        // Only relevant to lint implementations; :lint-rules is excluded above.
        disable += "UElementAsPsi"

        // Workaround for internal lint rule: `CannotEnableHidden`
        // we want to enable this in `:AnkiDroid` with `checkDependencies = true`

        // > Any issues that are specifically disabled in a library cannot be
        // > re-enabled in a dependent project. To fix this you need to also
        // > enable the issue in the library project.
        //
        // > (This also applies for issues that are off by default; they cannot
        // > just be enabled in a dependent project; they must also be enabled
        // > in all the libraries the project depends on.)
        enable += "LogConditional"

        // WrongThread is off in this non-Android lint client. lint-release.xml sets it
        // informational, and checkDependencies rejects raising a disabled library issue.
        // Informational both enables it and keeps :common:lint from failing the build.
        // ThreadConstraint is already enabled here.
        informational += "WrongThread"
    }
}
