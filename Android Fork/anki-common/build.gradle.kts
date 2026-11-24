// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.addAnkiBackendDependencies

plugins {
    id("ankidroid.android.library")
    id("ankidroid.android.compose")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

configure<LibraryExtension> {
    // code should live inside com.ichi2.anki
    // namespace must be unique for resources generation.
    namespace = "com.ichi2.anki.ankicommon"
    buildFeatures.buildConfig = true
    buildFeatures.viewBinding = true
    testFixtures.enable = true
}

dependencies {
    implementation(project(":common"))
    implementation(project(":common:android"))
    implementation(project(":libanki"))
    implementation(project(":compat"))

    addAnkiBackendDependencies(project)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.google.material)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(testFixtures(project(":anki-common")))
    testImplementation(testFixtures(project(":libanki")))
    testImplementation(testFixtures(project(":common")))
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.json)

    testFixturesImplementation(project(":common:android"))
    testFixturesImplementation(project(":libanki"))
    testFixturesImplementation(libs.androidx.core)
    testFixturesImplementation(libs.androidx.test.core)
    testFixturesImplementation(libs.jakewharton.timber)
    testFixturesImplementation(libs.kotlin.test)
}
