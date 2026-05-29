// SPDX-License-Identifier: GPL-3.0-or-later

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
}

configure<KtlintExtension> {
    version.set(libs.versions.ktlint.get())
}
