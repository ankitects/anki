// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.variant.HasHostTests
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.Variant
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.TaskProvider
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport

/** The local aggregate and the individual module unit test reports uploaded to Codecov. */
internal class UnitTestCoverageReports(
    private val app: Project,
    private val aggregate: TaskProvider<JacocoReport>,
) {
    private val runtimeModules by lazy { app.runtimeProjectDependencies("playDebugRuntimeClasspath").toSet() + app }
    private val coverageEnabled = app.isCoverageEnabled()
    private val moduleClasses = mutableMapOf<Project, MutableList<FileCollection>>()

    /**
     * Modules that contribute a CI report and a Codecov flag:
     *
     * * the app
     * * `playDebug` runtime dependencies
     *
     * (only if they register coverage)
     *
     * A module with no unit tests is still included when it registers coverage, so it can
     * produce a report that replaces an earlier upload.
     *
     * One [flag][CodecovFlag] is defined per returned module.
     *
     * @throws IllegalStateException if a module in the app's runtime graph has multiple debug
     * variants.
     */
    private val ciModules by lazy {
        val modules = runtimeModules.filterTo(mutableSetOf()) { it in moduleClasses }
        val ambiguousModules = modules.filter { moduleClasses.getValue(it).size > 1 }
        check(ambiguousModules.isEmpty()) {
            val paths = ambiguousModules.joinToString { it.path }
            "Cannot select one unit test suite per Codecov flag for $paths because each has multiple debug variants."
        }
        modules
    }

    /**
     * Runs unit tests for the app and supported modules in its runtime graph, then collects their
     * JaCoCo XML reports in `AnkiDroid/build/reports/codecov/` for CI to upload by module.
     * Each report includes coverage of the module's reachable project dependencies. Modules without
     * tests still get a report to replace their previous coverage. Tests are rerun; reports require
     * coverage to be enabled.
     */
    private val ciReports =
        app.tasks.register<Sync>("jacocoCiUnitTestReports") {
            group = "verification"
            description = "Runs unit tests, validates Codecov flags, and collects coverage XML by module."
            into(app.layout.buildDirectory.dir("reports/codecov"))
        }

    init {
        // Verify only when the CI reports are scheduled, but before their tests run. Creating the
        // task, as `tasks` and IDE task listings do, must not fail on a stale `.codecov.yml`.
        val ciReportsPath = "${app.path}:${ciReports.name}"
        app.gradle.taskGraph.whenReady {
            if (!hasTask(ciReportsPath)) return@whenReady
            val configFile =
                app.rootProject.layout.projectDirectory
                    .file(".codecov.yml")
            val config =
                app.providers
                    .fileContents(configFile)
                    .asText
                    .get()
            CodecovCarryforwardFlags.verify(
                config = config,
                activeFlags = ciModules.map { it.unitCoverageFlag },
            )
        }
    }

    fun registerAndroidSuite(
        module: Project,
        variant: Variant,
        classExcludes: List<String>,
        hasTests: () -> Boolean = { true },
    ) {
        val unitTest = (variant as? HasHostTests)?.hostTests?.get(HostTestBuilder.UNIT_TEST_TYPE)
        registerSuite(
            module = module,
            taskName = "test${variant.name.replaceFirstChar { it.uppercase() }}UnitTest",
            // Disabled host tests have no classpath. Use the production graph for their zero report.
            runtimeClasspath = (unitTest ?: variant).runtimeConfiguration.name,
            sources = module.files(variant.sources.java?.all, variant.sources.kotlin?.all),
            classes =
                module.androidCoverageClasses(
                    report = aggregate,
                    variant = variant,
                    classExcludes = classExcludes,
                ),
            hasTests = { unitTest != null && hasTests() },
        )
    }

    fun registerSuite(
        module: Project,
        taskName: String,
        runtimeClasspath: String,
        sources: FileCollection,
        classes: FileCollection,
        hasTests: () -> Boolean = { true },
    ) {
        val suites = moduleClasses.getOrPut(module) { mutableListOf() }
        suites.add(classes)

        aggregate.configure {
            if (module in runtimeModules) {
                classDirectories.from(classes)
                sourceDirectories.from(sources)
            }
        }
        module.includeTestExecutionData(
            report = aggregate,
            taskName = taskName,
            pattern = "**/*.exec",
            includeModule = { module in runtimeModules && hasTests() },
        )

        // A module owns one flag even if AGP discovers several debug variants. Keep their
        // registration from creating the same task twice, and check for ambiguity only in CI coverage.
        if (suites.size == 1) {
            registerSuiteReport(
                module = module,
                taskName = taskName,
                runtimeClasspath = runtimeClasspath,
                hasTests = hasTests,
            )
        }
    }

    /**
     * Registers [module]'s unit test coverage report, which CI uploads to Codecov under the module's flag.
     *
     * The report includes only the hits recorded by [module]'s own tests.
     */
    private fun registerSuiteReport(
        module: Project,
        taskName: String,
        runtimeClasspath: String,
        hasTests: () -> Boolean,
    ) {
        val flag = module.unitCoverageFlag.name
        val xml = app.layout.buildDirectory.file("reports/jacoco/unit-suites/$flag.xml")
        val suiteReport =
            app.tasks.register<JacocoReport>("jacocoReport_$flag") {
                ensureAndroidClassesAvailable()
                // Resolving another module's classpath is refused when this task is created
                // outside task-graph calculation, such as by `tasks`. Defer it to the task's inputs.
                val exercisedClasses by lazy { classesExercisedBy(module, runtimeClasspath) }
                classDirectories.from(app.provider { exercisedClasses })
                reports.xml.required.set(true)
                reports.xml.outputLocation.set(xml)
                reports.html.required.set(false)
                // Replace JacocoReport's default, which skips the task without execution data.
                // Modules without tests still need a report so Codecov can replace their previous hits.
                val collectCoverage = coverageEnabled
                setOnlyIf { collectCoverage }
            }
        module.includeTestExecutionData(
            report = suiteReport,
            taskName = taskName,
            pattern = "**/*.exec",
            includeModule = hasTests,
        )
        ciReports.configure {
            if (module in ciModules) {
                dependsOn(suiteReport)
                if (coverageEnabled) from(xml)
            }
        }
    }

    /**
     * AGP binds Android class artifacts to the aggregate report in [androidCoverageClasses].
     * Realizing that report makes the class providers available to each module's report.
     */
    private fun ensureAndroidClassesAvailable() {
        aggregate.get()
    }

    /**
     * The app's classes that [module]'s tests can reach: its own, plus those of its project
     * dependencies, so the module's tests are credited for hits in the dependencies they exercise.
     *
     * Call this lazily: modules register their classes as they are configured.
     */
    private fun classesExercisedBy(
        module: Project,
        runtimeClasspath: String,
    ): List<FileCollection> =
        (module.runtimeProjectDependencies(runtimeClasspath) + module)
            .filter { it in ciModules }
            .map { moduleClasses.getValue(it).single() }
}

/**
 * The module's Codecov flag; CI uploads each report under its file name without the extension.
 * For example, `:note-editor` uses the flag `note-editor-test`.
 * `-test` identifies unit tests. Use a distinct suffix if Android tests are split by module.
 */
private val Project.unitCoverageFlag: CodecovFlag get() = CodecovFlag(path)
