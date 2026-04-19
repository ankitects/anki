/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

/*
 Convention plugin: applies `com.android.library` and pins the settings
 shared across every Android library module in this project.

 This does not apply to the API module,
 */

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.artifacts.VersionCatalogsExtension

plugins {
    id("com.android.library")
}

// Type-safe `libs` accessors aren't available in precompiled script plugins,
// so read versions from the catalog explicitly.
val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
fun versionInt(alias: String): Int =
    libs.findVersion(alias).get().requiredVersion.toInt()

extensions.configure<LibraryExtension> {
    compileSdk = versionInt("compileSdk")

    defaultConfig {
        minSdk = versionInt("minSdk")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// Shared project-wide lint configuration.
apply(from = "${rootDir}/lint.gradle")

// Apply jacoco so module unit tests produce .exec files that
// AnkiDroid's jacocoUnitTestReport aggregates across modules.
apply(from = "${rootDir}/jacocoSupport.gradle")
