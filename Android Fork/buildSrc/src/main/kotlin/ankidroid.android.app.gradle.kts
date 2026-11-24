// SPDX-License-Identifier: GPL-3.0-or-later

/*
 Convention plugin: applies `com.android.application` plus Kotlin Parcelize,
 and pins the app's SDK and Java settings. Mirrors `ankidroid.android.library`.
 */

import com.android.build.api.dsl.ApplicationExtension
import com.ichi2.anki.gradle.configureAndroidLint
import com.ichi2.anki.gradle.libsVersionFor

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.parcelize")
}

extensions.configure<ApplicationExtension> {
    compileSdk = libsVersionFor("compileSdk").toInt()
    compileSdkMinor = libsVersionFor("compileSdkMinor").toInt()

    defaultConfig {
        minSdk = libsVersionFor("minSdk").toInt()
        // After Issue 13695: change .tests_emulator.yml
        targetSdk = libsVersionFor("targetSdk").toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Shared project-wide lint configuration.
configureAndroidLint()
