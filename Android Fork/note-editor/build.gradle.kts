// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension

plugins {
    id("ankidroid.android.library")
    id("ankidroid.android.compose")
}

configure<LibraryExtension> {
    namespace = "com.ichi2.anki.noteeditor"
}

dependencies {
    implementation(project(":common:android"))
    implementation(libs.androidx.core)

    testImplementation(testFixtures(project(":common:android")))
    testImplementation(libs.robolectric)
    testImplementation(libs.junit.vintage.engine)
    testRuntimeOnly(libs.junit.platform.launcher)
}
