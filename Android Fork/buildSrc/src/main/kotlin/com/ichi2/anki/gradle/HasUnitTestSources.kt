// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Project
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.provider.ValueSource
import org.gradle.api.provider.ValueSourceParameters
import org.gradle.api.tasks.util.PatternSet
import org.gradle.kotlin.dsl.getByType

/**
 * Tracks whether a library has unit-test sources as an input to the configuration cache.
 *
 * Moving from 0 to 1 tests changes this value, ensuring that code coverage is set up correctly.
 */
abstract class HasUnitTestSources : ValueSource<Boolean, HasUnitTestSources.Parameters> {
    interface Parameters : ValueSourceParameters {
        val sourceDirectories: ConfigurableFileCollection
    }

    override fun obtain(): Boolean =
        !parameters.sourceDirectories
            .asFileTree
            .matching(PatternSet().include("**/*.java", "**/*.kt"))
            .isEmpty

    companion object {
        // Library unit tests currently use the unflavored debug variant.
        fun Project.hasAndroidUnitTestSources(): Boolean {
            val sourceDirectories =
                extensions
                    .getByType<LibraryExtension>()
                    .sourceSets
                    .filter { it.name == "test" || it.name == "testDebug" }
                    .flatMap { sourceSet ->
                        files(sourceSet.java.directories, sourceSet.kotlin.directories).files
                    }
            return providers
                .of(HasUnitTestSources::class.java) {
                    parameters.sourceDirectories.from(sourceDirectories)
                }.get()
        }
    }
}
