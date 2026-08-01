// SPDX-FileCopyrightText: 2026 Ashish Yadav <mailtoashish693@gmail.com>
// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.LibraryExtension

plugins {
    id("ankidroid.android.library")
}

configure<LibraryExtension> {
    namespace = "com.ichi2.anki.widgets"
}

dependencies {
    implementation(project(":libanki"))

    implementation(libs.androidx.annotation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.jakewharton.timber)
}
