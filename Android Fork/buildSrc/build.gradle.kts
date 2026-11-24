// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    `kotlin-dsl`
    alias(libs.plugins.ktlint.gradle.plugin)
}

repositories {
    google()
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    // Needed by precompiled script plugins that apply `com.android.library`.
    // Pins AGP/KGP transitively, so call sites need `id("com.android.library")` etc. with no version.
    implementation("com.android.tools.build:gradle:${libs.versions.androidGradlePlugin.get()}")
    // Force the catalog version of KGP, otherwise it's overridden by AGP.
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("org.jetbrains.kotlin:compose-compiler-gradle-plugin:${libs.versions.kotlin.get()}")
    implementation("io.github.takahirom.roborazzi:roborazzi-gradle-plugin:${libs.versions.roborazzi.get()}")
    testImplementation(gradleTestKit())
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter)
    testImplementation("org.jacoco:org.jacoco.core:${libs.versions.jacoco.get()}")
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
    // Show nested Gradle build failures from TestKit in CI logs.
    testLogging.exceptionFormat = TestExceptionFormat.FULL
    // Rerun the path guard if the app build file is moved.
    inputs.files("../AnkiDroid/build.gradle.kts")
    // The screenshot fixture uses the same Android SDK as the main build.
    inputs.file("../gradle/libs.versions.toml")
    // The coverage fixture uses the app's real include list.
    inputs.file("../gradle/jacoco-includes.txt")
}

// Run from the repository root: ./gradlew -p buildSrc downloadTestKitDependencies
tasks.register<JavaExec>("downloadTestKitDependencies") {
    group = "build setup"
    description = "Warms the TestKit dependency cache without running test assertions."
    classpath = sourceSets.test.get().runtimeClasspath
    mainClass.set("com.ichi2.anki.gradle.DownloadTestKitDependenciesKt")
}

configure<KtlintExtension> {
    version.set(libs.versions.ktlint.get())
}
