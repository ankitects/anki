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

@Volatile
private var cachedLibs: VersionCatalog? = null

// A Project reference is required, so this can't be `by lazy { }`
private fun Project.libs(): VersionCatalog =
    cachedLibs ?: extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .also { cachedLibs = it }

fun Project.libsVersionFor(alias: String): String =
    libs().findVersion(alias).get().requiredVersion

// Provider<MinimalExternalModuleDependency> is tied to build-scoped services
// so must not come from cachedLibs.
fun Project.libsLibrary(alias: String): Provider<MinimalExternalModuleDependency> =
    extensions
        .getByType<VersionCatalogsExtension>()
        .named("libs")
        .findLibrary(alias)
        .get()
