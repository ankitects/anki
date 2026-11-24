// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class BackendTempLibraryServiceTest {
    @TempDir
    lateinit var projectDir: File

    @Test
    fun `reports the worker directory once across tasks and configuration cache reuse`() {
        writeFixture()
        val runner = runner("test", "moreTests", "--rerun-tasks")
        repeat(2) { run ->
            val result = runner.build()
            assertEquals(TaskOutcome.SUCCESS, result.task(":test")?.outcome, result.output)
            assertEquals(1, Regex("Anki backend test libraries are using").findAll(result.output).count(), result.output)
            assertTrue(result.output.contains("worker-temp"), result.output)
            assertTrue(result.output.contains("a".repeat(40)), result.output)
            assertFalse(result.output.contains("most recently used"), result.output)
            assertFalse(result.output.contains("rm "), result.output)
            if (run > 0) assertTrue(result.output.contains("Reusing configuration cache."), result.output)
        }
        val upToDate = runner("test", "moreTests").build()
        assertEquals(TaskOutcome.UP_TO_DATE, upToDate.task(":test")?.outcome, upToDate.output)
        assertFalse(upToDate.output.contains("Anki backend test libraries are using"), upToDate.output)
        assertFalse(runner("help").build().output.contains("Anki backend test libraries are using"))

        val cached = runner("clean", "test", "moreTests").build()
        assertEquals(TaskOutcome.FROM_CACHE, cached.task(":test")?.outcome, cached.output)
        assertFalse(cached.output.contains("Anki backend test libraries are using"), cached.output)

        val failed = runner("test", "-PfailTests=true").buildAndFail()
        assertEquals(TaskOutcome.FAILED, failed.task(":test")?.outcome, failed.output)
        assertTrue(failed.output.contains("Anki backend test libraries are using"), failed.output)
    }

    private fun runner(vararg tasks: String): GradleRunner =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withArguments(
                *tasks,
                "--build-cache",
                "--configuration-cache",
                "--stacktrace",
                "-Djava.io.tmpdir=${File(projectDir, "daemon-temp")}",
            )

    private fun writeFixture() {
        File(projectDir, "daemon-temp").mkdirs()
        File(projectDir, "worker-temp").mkdirs()
        File("../gradle/libs.versions.toml").copyTo(File(projectDir, "libs.versions.toml"))
        writeFile(
            "settings.gradle",
            """
            rootProject.name = 'backend-temp-library-regression'
            dependencyResolutionManagement {
                repositories { mavenCentral() }
                versionCatalogs { libs { from(files('libs.versions.toml')) } }
            }
            buildCache { local { directory = file('build-cache') } }
            """.trimIndent(),
        )
        writeFile("gradle.properties", "org.gradle.workers.max=2\n")
        val productionClasses =
            BackendTempLibraryService::class.java.protectionDomain.codeSource.location
                .toURI()
        writeFile(
            "build.gradle",
            """
            import com.ichi2.anki.gradle.BackendTempLibraryService
            import com.ichi2.anki.gradle.BackendTempLibraryServiceKt
            buildscript { dependencies { classpath files(new URI('$productionClasses')) } }
            apply plugin: 'java'
            dependencies {
                testImplementation libs.junit.jupiter
                testRuntimeOnly libs.junit.platform.launcher
            }
            def service = gradle.sharedServices.registerIfAbsent('backendTempLibraries', BackendTempLibraryService) {
                parameters.warnAboveBytes.set(0L)
            }
            tasks.register('moreTests', Test) {
                testClassesDirs = sourceSets.test.output.classesDirs
                classpath = sourceSets.test.runtimeClasspath
            }
            tasks.withType(Test).configureEach {
                useJUnitPlatform()
                BackendTempLibraryServiceKt.reportBackendTempLibraries(delegate, service)
                systemProperty 'java.io.tmpdir', file('worker-temp').absolutePath
                systemProperty 'fixture.fail', providers.gradleProperty('failTests').getOrElse('false')
            }
            """.trimIndent(),
        )
        writeFile(
            "src/test/java/FixtureTest.java",
            """
            import java.nio.file.Files;
            import java.nio.file.Path;
            import org.junit.jupiter.api.Test;
            class FixtureTest {
                @Test void leavesLibrary() throws Exception {
                    Files.writeString(Path.of(System.getProperty("java.io.tmpdir"),
                        "librsdroid-${"a".repeat(40)}-1.dylib"), "library");
                    if (Boolean.getBoolean("fixture.fail")) throw new AssertionError("intentional fixture failure");
                }
            }
            """.trimIndent(),
        )
    }

    private fun writeFile(
        path: String,
        contents: String,
    ) {
        File(projectDir, path).apply {
            parentFile.mkdirs()
            writeText(contents)
        }
    }
}
