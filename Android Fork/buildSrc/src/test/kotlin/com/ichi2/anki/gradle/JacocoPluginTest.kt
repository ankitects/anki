// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.equalTo
import org.hamcrest.Matchers.greaterThan
import org.hamcrest.Matchers.hasItems
import org.hamcrest.Matchers.not
import org.jacoco.core.tools.ExecFileLoader
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.io.File
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.xpath.XPathFactory

class JacocoPluginTest {
    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        val fixture = File(requireNotNull(javaClass.getResource("/jacoco-project")).toURI())
        fixture.walkTopDown().filter { it.isFile && it.extension == "fixture" }.forEach { source ->
            writeFile(source.relativeTo(fixture).path.removeSuffix(".fixture"), source.readText())
        }
        for (module in listOf("app", "library", "nested/core", "empty", "tooling")) {
            File(projectDir, module).mkdirs()
        }
        File("../gradle/libs.versions.toml").copyTo(File(projectDir, "libs.versions.toml"))
        val localProperties = Properties()
        File("../local.properties").takeIf { it.isFile }?.inputStream()?.use { localProperties.load(it) }
        // Preserve SDK settings without inheriting the checkout's coverage preference.
        localProperties.remove("enable_coverage")
        File(projectDir, "local.properties").outputStream().use { localProperties.store(it, null) }
        writeFile("gradle.properties", "org.gradle.workers.max=2\n")
        writeFile("gradle/jacoco-includes.txt", File("../gradle/jacoco-includes.txt").readText())
        writeFile("library/src/test/java/ExampleTest.java", "class ExampleTest {}")
        writeFile("nested/core/src/test/kotlin/ExampleTest.kt", "class ExampleTest")
        // Resources alone must not cause a unit-test task to be scheduled.
        writeFile("empty/src/test/resources/example.txt", "Not a test source")
    }

    @ParameterizedTest
    @CsvSource("false, false", "true, true")
    fun `coverage flag controls app and library unit test agents`(
        property: String,
        enabled: Boolean,
    ) {
        assertUnitTestCoverage(enabled, "-Penable_coverage=$property")
    }

    @Test
    fun `unit test coverage is enabled by default`() {
        assertUnitTestCoverage(true)
    }

    @Test
    fun `local properties can disable unit test coverage`() {
        File(projectDir, "local.properties").appendText("\nenable_coverage=false\n")
        assertUnitTestCoverage(false)
    }

    @Test
    fun `Gradle property overrides local coverage preference`() {
        File(projectDir, "local.properties").appendText("\nenable_coverage=false\n")
        assertUnitTestCoverage(true, "-Penable_coverage=true")
    }

    @Test
    fun `changing coverage preference invalidates configuration cache`() {
        File(projectDir, "gradle.properties").appendText("enable_coverage=false\n")
        assertUnitTestCoverage(false)
        val cached = assertUnitTestCoverage(false)
        assertThat(cached.output, containsString("Reusing configuration cache."))

        File(projectDir, "gradle.properties").appendText("enable_coverage=true\n")
        assertUnitTestCoverage(true)
    }

    @Test
    fun `unit agent retains production exceptions without instrumenting dependencies`() {
        writeFile(
            "app/src/main/java/com/ichi2/anki/coverage/CoveredApp.java",
            "package com.ichi2.anki.coverage; public class CoveredApp { public static int value() { return 7; } }",
        )
        writeFile(
            "app/src/main/java/androidx/drawerlayout/widget/ClosableDrawerLayout.java",
            "package androidx.drawerlayout.widget; " +
                "public class ClosableDrawerLayout { public static int value() { return 9; } }",
        )
        writeFile(
            "app/src/test/java/org/robolectric/coverage/Dependency.java",
            "package org.robolectric.coverage; public class Dependency { public static int value() { return 11; } }",
        )
        writeFile(
            "library/src/main/java/androidx/appcompat/widget/DialogTitleView.java",
            """
            package androidx.appcompat.widget;
            public class DialogTitleView {
                public static int value() { return 19; }
                public static class Nested { public static int value() { return 23; } }
            }
            """.trimIndent(),
        )
        writeFile(
            "library/src/main/kotlin/com/google/android/material/snackbar/SnackbarPackageWorkaround.kt",
            """
            package com.google.android.material.snackbar
            fun workaroundValue() = 37
            """.trimIndent(),
        )
        writeFile(
            "app/src/test/java/regression/CoverageTest.java",
            """
            package regression;
            public class CoverageTest {
                @org.junit.Test public void executesAppAndDependencies() {
                    org.junit.Assert.assertEquals(7, com.ichi2.anki.coverage.CoveredApp.value());
                    org.junit.Assert.assertEquals(9, androidx.drawerlayout.widget.ClosableDrawerLayout.value());
                    org.junit.Assert.assertEquals(11, org.robolectric.coverage.Dependency.value());
                    org.junit.Assert.assertEquals(19, androidx.appcompat.widget.DialogTitleView.value());
                    org.junit.Assert.assertEquals(23, androidx.appcompat.widget.DialogTitleView.Nested.value());
                    org.junit.Assert.assertEquals(37, com.google.android.material.snackbar.SnackbarPackageWorkaroundKt.workaroundValue());
                }
            }
            """.trimIndent(),
        )
        File(projectDir, "app/build.gradle").appendText(
            """

            dependencies {
                testImplementation libs.junit.vintage.engine
            }
            tasks.withType(Test).configureEach { useJUnit() }
            """.trimIndent(),
        )

        val result =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withTestKitDir(File("build/test-kit").absoluteFile)
                .withPluginClasspath()
                .withArguments(":app:testPlayDebugUnitTest", "--configuration-cache", "--stacktrace")
                .build()
        assertThat(result.task(":app:testPlayDebugUnitTest")?.outcome, equalTo(TaskOutcome.SUCCESS))

        val coverage = ExecFileLoader().apply { load(File(projectDir, "app/build/jacoco/testPlayDebugUnitTest.exec")) }
        val coveredNames =
            coverage.executionDataStore.contents
                .filter { it.probes.any { covered -> covered } }
                .map { it.name }
        assertThat(
            coveredNames,
            hasItems("com/ichi2/anki/coverage/CoveredApp", "androidx/drawerlayout/widget/ClosableDrawerLayout"),
        )
        val preserved =
            listOf(
                "androidx/drawerlayout/widget/ClosableDrawerLayout",
                "androidx/appcompat/widget/DialogTitleView",
                "androidx/appcompat/widget/DialogTitleView\$Nested",
                "com/google/android/material/snackbar/SnackbarPackageWorkaroundKt",
            )
        for (name in preserved) assertThat(coveredNames, hasItems(name))
        assertThat(coveredNames.any { it.startsWith("org/robolectric/") }, equalTo(false))

        // Build the module reports from the app's tests alone: the library's and core's test
        // tasks are excluded, so their reports have no execution data.
        val tasks = arrayOf(":app:jacocoCiUnitTestReports", "-x", ":library:testDebugUnitTest", "-x", ":nested:core:test")
        val reportResult = runCoverage(*tasks)
        assertThat(reportResult.task(":app:jacocoUnitTestReport")?.outcome, equalTo(TaskOutcome.SUCCESS))
        val reports = File(projectDir, "app/build/reports/codecov")
        // A class in :library that the app's tests exercise (see `preserved` above).
        val libraryClass = "androidx/appcompat/widget/DialogTitleView"
        // The app's tests are credited for hits in a dependency's classes...
        assertThat(coveredLines(reports.resolve("app-test.xml"), libraryClass), greaterThan(0))
        // ...and the library's report is not: each module reports only its own tests' hits.
        // The library report still exists, with zero coverage, although its tests did not run.
        assertThat(coveredLines(reports.resolve("library-test.xml"), libraryClass), equalTo(0))

        val cached = runCoverage(*tasks)
        assertThat(cached.output, containsString("Reusing configuration cache."))
    }

    @Test
    fun `full CI runs seed a flag for each runtime suite`() {
        // Every module on the app's runtime graph gets a report task, including :empty, which has
        // no tests: CI uploads a report for each flag so stale coverage is replaced.
        val output = runGradle(":app:jacocoCiUnitTestReports", "--dry-run").output
        for (flag in listOf("app-test", "library-test", "nested_core-test", "empty-test")) {
            assertThat(output, containsString(":app:jacocoReport_$flag SKIPPED"))
        }
        // :tooling is not on the app's runtime classpath, so it has no report.
        assertThat(output, not(containsString("tooling-test")))
    }

    /**
     * Regression: listing tasks failed with
     * "Resolution of the configuration ':library:debugUnitTestRuntimeClasspath' was attempted
     * without an exclusive lock".
     *
     * The per-module `jacocoReport_*` tasks belong to `:app`, but each one's classes depend on the
     * project dependencies of a *different* module's unit test classpath. `tasks` realizes every
     * task while it runs, outside task-graph calculation, and Gradle then holds only the lock of
     * the project being reported on. Resolving another project's configuration from the task's
     * configure action is refused, so the whole `tasks` report (and IDE task listings) failed.
     *
     * Correct behavior: the per-module report tasks are listed without resolving other projects'
     * configurations; that resolution is deferred until the report's inputs are needed.
     */
    @Test
    fun `listing tasks does not resolve other modules' classpaths`() {
        // `:app:tasks --all` realizes :app's tasks, including jacocoReport_library-test, whose
        // classes come from :library's debugUnitTestRuntimeClasspath. On the buggy revision,
        // build() throws here because Gradle refuses that cross-project resolution.
        // `--parallel` matches the repository's gradle.properties: Gradle enforces per-project
        // locks only in parallel mode, so the fixture's default serial build passes either way.
        val output = runGradle(":app:tasks", "--all", "--parallel").output
        // The suite reports are still registered and visible to developers.
        assertThat(output, containsString("jacocoReport_library-test"))
        assertThat(output, containsString("jacocoCiUnitTestReports"))
    }

    @Test
    fun `CI seeds a flag for an Android library with its unit test component disabled`() {
        // Keep :empty in the app's runtime graph, but disable its debug unit test component.
        // AGP then omits the test task and its classpath. Codecov still needs this module's
        // zero-coverage report to replace any past uploads for its flag.
        File(projectDir, "build.gradle").appendText(
            "\n" +
                """
                project(':empty').androidComponents {
                    beforeVariants(selector().withBuildType('debug')) {
                        it.hostTests['UnitTest'].enable = false
                    }
                }
                """.trimIndent() + "\n",
        )

        // Skip unrelated suites to exercise collection without needing any execution data.
        // On the buggy revision, configuring this task fails on :empty's missing unit test
        // classpath. The report should instead use :empty's production classes and show zero hits.
        runGradle(
            ":app:jacocoCiUnitTestReports",
            "-x",
            ":app:testPlayDebugUnitTest",
            "-x",
            ":library:testDebugUnitTest",
            "-x",
            ":nested:core:test",
        )
        val report = File(projectDir, "app/build/reports/codecov/empty-test.xml")
        assertThat(coveredLines(report, "regression/Uncovered"), equalTo(0))
    }

    @Test
    fun `flavored Android library outside app runtime does not break coverage`() {
        // Coverage used to register the same report task for both debug variants of any Android
        // library. Even an unrelated library broke Gradle configuration before any task could run.
        // The app's coverage reports should only include modules it uses at runtime.
        File(projectDir, "app/build.gradle").appendText("\nconfigurations.runtimeOnly.dependencies.removeIf { it.name == 'empty' }\n")
        File(projectDir, "build.gradle").appendText(
            "\n" +
                """
                project(':empty').android {
                    flavorDimensions += 'tier'
                    productFlavors {
                        free { dimension 'tier' }
                        paid { dimension 'tier' }
                    }
                }
                """.trimIndent() + "\n",
        )
        // Match the fixture's Codecov flags to its new runtime graph so they cannot cause the failure.
        val config = File(projectDir, ".codecov.yml")
        config.writeText(config.readLines().filterNot { it.startsWith("  empty-test:") }.joinToString("\n", postfix = "\n"))

        // On the buggy revision, even help fails because the second debug variant tries to register
        // jacocoReport_empty-test again. The coverage graph should also work and exclude :empty.
        assertThat(runGradle("help").task(":help")?.outcome, equalTo(TaskOutcome.SUCCESS))
        val reports = runGradle(":app:jacocoCiUnitTestReports", "--dry-run").output
        assertThat(reports, containsString(":app:jacocoReport_library-test SKIPPED"))
        assertThat(reports, not(containsString("empty-test")))
    }

    /**
     * Regression: a stale `flags:` block in `.codecov.yml` broke every build that created
     * `jacocoCiUnitTestReports`, not only the CI build that runs it.
     *
     * The flag check ran in the task's configure action, so `tasks` (and IDE task listings),
     * which create all tasks without scheduling them, failed with "Could not create task
     * ':app:jacocoCiUnitTestReports'. > Replace the flags section...". Developers hit this as soon
     * as they added, removed or renamed a module, before they had a chance to update the file.
     *
     * Correct behavior: listing tasks still works; only a build that schedules the CI reports
     * fails (see `CI rejects carryforward flags for retired suites`).
     */
    @Test
    fun `stale Codecov flags do not break listing tasks`() {
        // Retire :empty's flag while :empty is still on the app's runtime graph, as if a module
        // had been added without updating .codecov.yml.
        val config = File(projectDir, ".codecov.yml")
        config.writeText(config.readLines().filterNot { it.startsWith("  empty-test:") }.joinToString("\n", postfix = "\n"))

        // `tasks --all` creates jacocoCiUnitTestReports to describe it. On the buggy revision,
        // build() throws here because creating the task runs the flag check.
        val output = runGradle(":app:tasks", "--all").output
        assertThat(output, containsString("jacocoCiUnitTestReports"))
    }

    @Test
    fun `CI rejects carryforward flags for retired suites`() {
        // A full run cannot replace a retired module's upload. Codecov would keep its hits
        // on surviving dependency classes if the old module were still allowed to carry forward.
        File(projectDir, "app/build.gradle").appendText("\nconfigurations.implementation.dependencies.removeIf { it.name == 'library' }\n")
        // The remaining :empty runtime dependency still brings in :nested:core. Only :library
        // has left the app graph; it still exists in settings, as a library used elsewhere could.
        val failure =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withTestKitDir(File("build/test-kit").absoluteFile)
                .withPluginClasspath()
                .withArguments(":app:jacocoCiUnitTestReports", "--dry-run")
                .buildAndFail()

        // The original task succeeds and would upload the remaining modules without clearing the old flag.
        val replacement = failure.output.substringAfter("Replace the flags section in .codecov.yml with:", "")
        assertThat(replacement, containsString("nested_core-test: *unit"))
        assertThat(replacement, not(containsString("library-test:")))
    }

    @ParameterizedTest
    @CsvSource("nested_core", "_nested_core")
    fun `CI rejects module paths with reserved underscores`(moduleName: String) {
        // Flatten the nested project while reusing its existing directory and sources. The old
        // path :nested:core and the new path :nested_core would produce the same flag, so
        // reject literal underscores before they can reuse a retired module's coverage.
        val settings = File(projectDir, "settings.gradle")
        settings.writeText(settings.readText().replace(":nested:core", ":$moduleName"))
        settings.appendText("\nproject(':$moduleName').projectDir = new File(settingsDir, 'nested/core')\n")
        val rootBuild = File(projectDir, "build.gradle")
        rootBuild.writeText(rootBuild.readText().replace(":nested:core", ":$moduleName"))

        val failure =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withTestKitDir(File("build/test-kit").absoluteFile)
                .withPluginClasspath()
                .withArguments(":app:jacocoCiUnitTestReports", "--dry-run")
                .buildAndFail()

        // With escaping, the failure instead suggests a replacement flag. Require the
        // module-path error so this test catches that behavior as well as a silent collision.
        assertThat(failure.output, containsString("Invalid AnkiDroid module path ':$moduleName'"))
        assertThat(failure.output, containsString("Underscores are reserved for nested module separators"))
    }

    @ParameterizedTest
    @CsvSource(":feature:browser:collection-import-export-and-synchronization", ":feature:browser:collection+reports")
    fun `CI rejects an invalid Codecov flag for a renamed module`(modulePath: String) {
        // Gradle accepts long names and characters such as + in project paths. Renaming the
        // fixture reuses the nested module's sources while making its old Codecov flag stale.
        // The generated flag must satisfy Codecov's rules before it can be used for uploads.
        val settings = File(projectDir, "settings.gradle")
        settings.writeText(settings.readText().replace(":nested:core", modulePath))
        settings.appendText("\nproject('$modulePath').projectDir = new File(settingsDir, 'nested/core')\n")
        val rootBuild = File(projectDir, "build.gradle")
        rootBuild.writeText(rootBuild.readText().replace(":nested:core", modulePath))
        // Gradle 9 requires directories for the new parent projects to exist, even though the
        // renamed leaf still points to the original directory with its build and sources.
        File(projectDir, "feature/browser").mkdirs()

        val failure =
            GradleRunner
                .create()
                .withProjectDir(projectDir)
                .withTestKitDir(File("build/test-kit").absoluteFile)
                .withPluginClasspath()
                .withArguments(":app:jacocoCiUnitTestReports", "--dry-run")
                .buildAndFail()

        // Fail with the rejected flag and the Codecov constraints, rather than printing an
        // unusable replacement block or silently changing the module's flag.
        val flag = modulePath.removePrefix(":").replace(':', '_') + "-test"
        assertThat(failure.output, containsString("Invalid Codecov flag '$flag'"))
        assertThat(failure.output, containsString("1 to 45 letters, digits, underscores, hyphens, or dots"))
    }

    private fun coveredLines(
        report: File,
        className: String,
    ): Int {
        val document =
            DocumentBuilderFactory
                .newInstance()
                .apply { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                .newDocumentBuilder()
                .parse(report)
        return XPathFactory
            .newInstance()
            .newXPath()
            .evaluate("/report/package/class[@name='$className']/counter[@type='LINE']/@covered", document)
            .toInt()
    }

    @Test
    fun `report covers runtime modules and skips empty library test tasks`() {
        val output = dryRunCoverage()

        for (module in listOf("app", "library", "nested/core", "empty")) {
            assertThat(output, containsString("$module/src/main/kotlin"))
        }
        assertThat(output, not(containsString("tooling/src/main")))

        assertThat(output, containsString(":app:testPlayDebugUnitTest SKIPPED"))
        assertThat(output, containsString(":library:testDebugUnitTest SKIPPED"))
        assertThat(output, containsString(":nested:core:test SKIPPED"))
        assertThat(output, not(containsString(":empty:testDebugUnitTest")))
        assertThat(output, not(containsString(":tooling:test")))
    }

    @Test
    fun `coverage reports do not realize unrelated tasks`() {
        // AGP 9.5 registers Compose Preview tasks that fail during configuration
        // for variants without unit tests. Coverage must leave those tasks lazy.
        for (module in listOf("app", "library")) {
            File(projectDir, "$module/build.gradle").appendText(
                """

                tasks.register('unrelatedPreviewTask') {
                    throw new GradleException('Coverage must not realize unrelated tasks')
                }
                """.trimIndent(),
            )
        }

        val output = runCoverage(":app:jacocoAndroidTestReport", "--dry-run").output
        assertThat(output, containsString(":app:jacocoUnitTestReport SKIPPED"))
        assertThat(output, containsString(":app:jacocoAndroidTestReport SKIPPED"))
    }

    @Test
    fun `library without unit tests is included as uncovered in the report`() {
        // A valid, empty execution file represents a run that never touched this class.
        ExecFileLoader().save(File(projectDir, "app/build/jacoco/testPlayDebugUnitTest.exec"), false)

        // Run the real report with Android compilation, skipping only test execution.
        val result =
            runCoverage(
                "-x",
                ":app:testPlayDebugUnitTest",
                "-x",
                ":library:testDebugUnitTest",
                "-x",
                ":nested:core:test",
            )
        assertThat(result.task(":app:jacocoUnitTestReport")?.outcome, equalTo(TaskOutcome.SUCCESS))

        val report =
            DocumentBuilderFactory
                .newInstance()
                .apply { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                .newDocumentBuilder()
                .parse(File(projectDir, "app/build/reports/jacoco/jacocoUnitTestReport/jacocoUnitTestReport.xml"))
        val xpath = XPathFactory.newInstance().newXPath()
        val counter = "/report/package/class[@name='regression/Uncovered']/counter[@type='LINE']"
        assertThat(xpath.evaluate("$counter/@covered", report), equalTo("0"))
        assertThat(xpath.evaluate("$counter/@missed", report).toInt(), greaterThan(0))
    }

    @Test
    fun `adding the first test in a custom source root updates the cached task graph`() {
        val before = dryRunCoverage()
        assertThat(before, not(containsString(":empty:testDebugUnitTest")))

        val cached = dryRunCoverage()
        assertThat(cached, containsString("Reusing configuration cache."))

        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val after = dryRunCoverage()
        assertThat(after, containsString(":empty:testDebugUnitTest SKIPPED"))
    }

    @Test
    fun `removing the last test updates the cached task graph`() {
        writeFile("empty/custom-tests/ExampleTest.kt", "class ExampleTest")
        val before = dryRunCoverage()
        assertThat(before, containsString(":empty:testDebugUnitTest SKIPPED"))

        assertThat(File(projectDir, "empty/custom-tests/ExampleTest.kt").delete(), equalTo(true))
        val after = dryRunCoverage()
        assertThat(after, not(containsString(":empty:testDebugUnitTest")))
    }

    private fun dryRunCoverage(): String = runCoverage("--dry-run").output

    internal fun downloadDependencies() {
        setUp()
        // Configuration-cache serialization resolves dependency artifacts even during a dry run.
        runCoverage(":app:jacocoAndroidTestReport", "--dry-run")
    }

    private fun assertUnitTestCoverage(
        enabled: Boolean,
        vararg arguments: String,
    ): BuildResult {
        val result = runGradle(":app:inspectUnitTestCoverage", *arguments)
        for (task in listOf(":app:testPlayDebugUnitTest", ":library:testDebugUnitTest", ":nested:core:test")) {
            assertThat(result.output, containsString("$task coverage=$enabled"))
        }
        return result
    }

    private fun runCoverage(vararg arguments: String): BuildResult = runGradle(":app:jacocoUnitTestReport", *arguments)

    private fun runGradle(vararg arguments: String): BuildResult =
        GradleRunner
            .create()
            .withProjectDir(projectDir)
            .withTestKitDir(File("build/test-kit").absoluteFile)
            .withPluginClasspath()
            .withArguments("--configuration-cache", "--stacktrace", *arguments)
            .build()

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
