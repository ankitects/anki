// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.provider.Provider
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.TaskProvider
import java.io.File

object VersionCodeTasks {
    @JvmStatic
    fun registerValidationTask(
        project: Project,
        versionCode: Int?,
    ): TaskProvider<Task> =
        with(project) {
            val explicitBase = providers.gradleProperty("versionCodeBase")
            val baseCode = atRevision(providers, rootDir, explicitBase.getOrElse("HEAD^"))
            val headCode = atRevision(providers, rootDir, "HEAD")
            val currentScript = providers.fileContents(layout.projectDirectory.file(buildFile.name)).asText
            tasks.register("validateVersionCode") {
                group = "verification"
                description = "Validates the versionCode bump."
                doLast {
                    // Require a literal in the current script as well as the comparison revision.
                    VersionCode.read(currentScript.get())
                    // Use CI's base, or compare local changes to HEAD (HEAD^ once committed).
                    val previousCode = if (explicitBase.isPresent || headCode.get() == versionCode) baseCode.get() else headCode.get()
                    VersionCode.validate(previousCode, versionCode)
                }
            }
        }

    /** Read whichever script exists at the comparison revision, including across a Kotlin DSL migration. */
    private fun atRevision(
        providers: ProviderFactory,
        rootDir: File,
        revision: String,
    ): Provider<Int> {
        val scripts =
            listOf("AnkiDroid/build.gradle", "AnkiDroid/build.gradle.kts").map { path ->
                providers.exec {
                    workingDir(rootDir)
                    commandLine("git", "show", "$revision:$path")
                    isIgnoreExitValue = true
                }
            }
        return providers.provider {
            val script =
                requireNotNull(scripts.singleOrNull { it.result.get().exitValue == 0 }) {
                    "Expected exactly one AnkiDroid build.gradle or build.gradle.kts at '$revision'."
                }
            VersionCode.read(script.standardOutput.asText.get())
        }
    }
}
