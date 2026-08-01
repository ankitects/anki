// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension

plugins {
    id("ankidroid.android.library")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

configure<LibraryExtension> {
    namespace = "com.ichi2.anki.common.android"
}

dependencies {
    implementation(project(":common"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.google.material)
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.junit)
}
