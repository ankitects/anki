// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

// Type-safe `libs` accessors aren't available in precompiled script plugins,
// so read versions from the `libs` catalog explicitly.

private fun Project.libs(): VersionCatalog =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")

fun Project.libsVersionFor(alias: String): String = libs().findVersion(alias).get().requiredVersion

fun Project.libsLibrary(alias: String): Provider<MinimalExternalModuleDependency> = libs().findLibrary(alias).get()
