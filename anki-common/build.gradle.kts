// SPDX-License-Identifier: GPL-3.0-or-later

import com.android.build.api.dsl.LibraryExtension

plugins {
    id("ankidroid.android.library")
}

configure<LibraryExtension> {
    // code should live inside com.ichi2.anki
    // namespace must be unique for resources generation.
    namespace = "com.ichi2.anki.ankicommon"
    buildFeatures.buildConfig = false
}

dependencies {
    implementation(project(":common"))
    implementation(project(":libanki"))
}
