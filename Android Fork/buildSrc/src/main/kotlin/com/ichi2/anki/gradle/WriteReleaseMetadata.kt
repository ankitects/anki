// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import groovy.json.JsonOutput
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction

/**
 * Exports the configured release version, before any release-script version bump or variant suffix.
 *
 * Write JSON to a file because Gradle and plugins can also log to stdout. Consumers should read the
 * file directly instead of redirecting Gradle output, which may mix those messages with the JSON.
 *
 * Example output:
 * ```json
 * {
 *   "versionName": "2.26.0alpha0",
 *   "versionCode": 22600100,
 *   "releaseType": "alpha"
 * }
 * ```
 */
@CacheableTask
abstract class WriteReleaseMetadata : DefaultTask() {
    @get:Input
    abstract val versionName: Property<String>

    @get:Input
    abstract val versionCode: Property<Int>

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    init {
        outputFile.convention(project.layout.buildDirectory.file("outputs/release-metadata.json"))
    }

    @TaskAction
    fun writeMetadata() {
        val code = versionCode.get()
        require(code > 0) { "versionCode must be positive: $code" }
        // The hundreds digit encodes the release type in AnkiDroid's AbbCCtDD version code.
        val releaseType =
            when (code / 100 % 10) {
                0 -> "dev"
                1 -> "alpha"
                2 -> "beta"
                3 -> "public"
                else -> error("Unknown release type in versionCode: $code")
            }
        val metadata =
            mapOf(
                "versionName" to versionName.get(),
                "versionCode" to code,
                "releaseType" to releaseType,
            )
        val destination = outputFile.get().asFile
        destination.parentFile.mkdirs()
        destination.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(metadata)) + "\n")
    }
}
