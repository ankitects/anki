// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Action
import org.gradle.api.Task
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test
import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Reports persistent rsdroid-testing libraries after the build's test tasks finish. Never deletes files. */
abstract class BackendTempLibraryService :
    BuildService<BackendTempLibraryService.Params>,
    AutoCloseable {
    interface Params : BuildServiceParameters {
        val warnAboveBytes: Property<Long>
    }

    private val directories = ConcurrentHashMap.newKeySet<File>()

    fun recordDirectory(directory: File) {
        directories.add(directory.canonicalFile)
    }

    override fun close() {
        backendTempLibraryReport(directories, parameters.warnAboveBytes.get())?.let {
            Logging.getLogger(BackendTempLibraryService::class.java).warn(it)
        }
    }
}

/** Uses an execution-time action so skipped/cached tests do not produce a report. */
fun Test.reportBackendTempLibraries(service: Provider<BackendTempLibraryService>) {
    // Make the default explicit: a forked JVM need not inherit the daemon's java.io.tmpdir.
    systemProperty("java.io.tmpdir", systemProperties["java.io.tmpdir"] ?: project.providers.systemProperty("java.io.tmpdir").get())
    usesService(service)
    doFirst(RecordBackendTempDirectory(service))
}

private class RecordBackendTempDirectory(
    private val service: Provider<BackendTempLibraryService>,
) : Action<Task> {
    override fun execute(task: Task) {
        val test = task as Test
        // Read the final fork arguments, including overrides supplied after our configuration.
        val path = test.allJvmArgs.last { it.startsWith("-Djava.io.tmpdir=") }.substringAfter('=')
        val directory = File(path).let { if (it.isAbsolute) it else File(test.workingDir, path) }
        service.get().recordDirectory(directory)
    }
}

// Names from RustBackendLoader.getPathFromResourceStream and File.numberedCopy in rsdroid-testing.
private val checksumAndCopy = "[0-9a-f]{40}(?:-[1-9][0-9]*)?"
private val backendLibraryName = Regex("(?:librsdroid-$checksumAndCopy\\.(?:so|dylib)|rsdroid-$checksumAndCopy\\.dll)")

/** A snapshot of library sizes; modification times cannot tell us which versions are still in use. */
internal fun backendTempLibraryReport(
    directories: Set<File>,
    warnAboveBytes: Long,
): String? {
    val libraries =
        directories
            .flatMap { it.listFiles().orEmpty().asList() }
            .filter { backendLibraryName.matches(it.name) && Files.isRegularFile(it.toPath(), NOFOLLOW_LINKS) }
            .sortedBy { it.path }
            .map { it to it.length() }
    val total = libraries.sumOf { it.second }
    if (libraries.isEmpty() || total <= warnAboveBytes) return null
    return buildString {
        appendLine("Anki backend test libraries are using ${total.asMiB()} MiB (${libraries.size} files):")
        libraries.forEach { (file, bytes) -> appendLine("    ${bytes.asMiB()} MiB  $file") }
        appendLine("rsdroid-testing keeps an original and up to five copies per checksum by default, for reuse across classloaders.")
        appendLine("By default these files live outside the project and are not removed by Gradle clean.")
        appendLine("To reclaim space, stop tests in all checkouts and IDEs, then delete the listed library files.")
        append("Needed libraries will be extracted again on the next test run. Leave .lock files in place.")
    }
}

private fun Long.asMiB(): String = String.format(Locale.ROOT, "%.1f", this / (1024.0 * 1024))
