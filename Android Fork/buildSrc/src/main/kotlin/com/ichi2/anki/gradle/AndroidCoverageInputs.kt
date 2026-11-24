// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.Directory
import org.gradle.api.file.RegularFile
import org.gradle.api.model.ObjectFactory
import javax.inject.Inject

/**
 * Provides the compiled classes for an Android variant to a JaCoCo coverage report.
 * AGP populates [jars] and [directories]; [classes] combines them and expands the JARs.
 * Gradle providers preserve dependencies on the compilation tasks.
 */
abstract class AndroidCoverageInputs {
    /** Factory for the coverage input properties and file collection. */
    @get:Inject
    protected abstract val objects: ObjectFactory

    /** Archive operations used to expand class JARs. */
    @get:Inject
    protected abstract val archives: ArchiveOperations

    /** Class JARs supplied by AGP for the selected variant. */
    val jars = objects.listProperty(RegularFile::class.java)

    /** Class directories supplied by AGP for the selected variant. */
    val directories = objects.listProperty(Directory::class.java)

    /** Combined class files from directories and expanded JARs for JaCoCo. */
    val classes =
        // Capture the service so the provider does not serialize this input holder.
        archives.let { archiveOperations ->
            objects
                .fileCollection()
                .from(
                    directories,
                    jars.map { files -> files.map { archiveOperations.zipTree(it) } },
                ).asFileTree
        }
}
