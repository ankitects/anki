// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class VersionCatalogsTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `version catalog edits are visible when reusing the Gradle daemon`() {
        val buildLogicUri =
            Class
                .forName("com.ichi2.anki.gradle.VersionCatalogsKt")
                .protectionDomain.codeSource.location
                .toURI()
        File(projectDir, "settings.gradle").writeText("rootProject.name = 'version-catalog-cache-regression'")
        File(projectDir, "build.gradle").writeText(
            """
            buildscript {
                dependencies {
                    classpath files(new URI('$buildLogicUri'))
                }
            }

            def targetSdk = com.ichi2.anki.gradle.VersionCatalogsKt.libsVersionFor(project, 'targetSdk')
            tasks.register('showTargetSdk') {
                doLast {
                    println('targetSdk=' + targetSdk)
                    println('gradleRuntime=' + java.lang.management.ManagementFactory.runtimeMXBean.name)
                }
            }
            """.trimIndent(),
        )
        val catalog = File(projectDir, "gradle/libs.versions.toml")
        catalog.parentFile.mkdirs()
        val runner =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withTestKitDir(File(projectDir, "test-kit"))
                .withArguments("showTargetSdk", "--offline", "--stacktrace")

        catalog.writeText("[versions]\ntargetSdk = \"36\"\n")
        val firstBuild = runner.build().output
        assertEquals("36", firstBuild.outputValue("targetSdk"))

        catalog.writeText("[versions]\ntargetSdk = \"37\"\n")
        val secondBuild = runner.build().output
        assertEquals(firstBuild.outputValue("gradleRuntime"), secondBuild.outputValue("gradleRuntime"), "Reuse the same daemon")
        assertEquals("37", secondBuild.outputValue("targetSdk"))
    }

    private fun String.outputValue(key: String): String = lineSequence().single { it.startsWith("$key=") }.substringAfter('=')
}
