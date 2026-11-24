// SPDX-License-Identifier: GPL-3.0-or-later

package com.ichi2.anki.gradle

import com.android.build.api.artifact.ScopedArtifact
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.api.variant.HostTestBuilder
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import com.android.build.api.variant.ScopedArtifacts
import com.android.build.api.variant.Variant
import com.ichi2.anki.gradle.HasUnitTestSources.Companion.hasAndroidUnitTestSources
import org.gradle.api.DomainObjectSet
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.file.FileTree
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.extra
import org.gradle.kotlin.dsl.get
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.tasks.JacocoReport

private class JacocoLibraryVariants(
    val variants: DomainObjectSet<Variant>,
)

/** Captures library variants before any library can finish configuring. */
fun Project.registerJacocoLibraryVariants() {
    allprojects {
        val variants = objects.domainObjectSet(Variant::class.java)
        extensions.add("jacocoLibraryVariants", JacocoLibraryVariants(variants))
        pluginManager.withPlugin("com.android.library") {
            extensions.configure<LibraryAndroidComponentsExtension> {
                onVariants(selector().withBuildType("debug")) { variant ->
                    variants.add(variant)
                }
            }
        }
    }
}

/** Registers the merged, unit-test, and Android-test coverage reports. */
fun Project.registerJacocoCoverageReports(
    fileFilter: List<String>,
    openReportRequested: Boolean,
    linuxHtmlCmd: String?,
) {
    // Our merge report task
    val mergedReport =
        tasks.register<JacocoReport>("jacocoTestReport") {
            configureJacocoReportOutput(
                layout = layout,
                openReportRequested = openReportRequested,
                linuxHtmlCmd = linuxHtmlCmd,
            )
        }

    // A unit-test only report task
    val unitTestReport =
        tasks.register<JacocoReport>("jacocoUnitTestReport") {
            configureJacocoReportOutput(
                layout = layout,
                openReportRequested = openReportRequested,
                linuxHtmlCmd = linuxHtmlCmd,
            )
        }

    // A connected android tests only report task
    val androidTestReport =
        tasks.register<JacocoReport>("jacocoAndroidTestReport") {
            configureJacocoReportOutput(
                layout = layout,
                openReportRequested = openReportRequested,
                linuxHtmlCmd = linuxHtmlCmd,
            )
        }

    val unitCoverage =
        UnitTestCoverageReports(
            app = project,
            aggregate = unitTestReport,
        )
    configureAndroidAppCoverage(
        unitCoverage = unitCoverage,
        androidTestReport = androidTestReport,
        mergedReport = mergedReport,
        androidTestVariantName = rootProject.androidTestVariantName,
        classExcludes = fileFilter,
    )

    // Libraries may already be configured. Subscribe to the captured variants,
    // then select runtime dependencies when the report's graph is available.
    for (module in rootProject.allprojects.filter { it != project }) {
        module.configureAndroidLibraryCoverage(
            unitCoverage = unitCoverage,
            classExcludes = fileFilter,
        )
        module.configureJvmCoverage(
            unitCoverage = unitCoverage,
            classExcludes = fileFilter,
        )
    }
}

/**
 * Connects app reports to debug unit tests and the selected emulator-test variant.
 * The merged report uses the emulator variant's classes and both tests' execution data.
 */
private fun Project.configureAndroidAppCoverage(
    unitCoverage: UnitTestCoverageReports,
    androidTestReport: TaskProvider<JacocoReport>,
    mergedReport: TaskProvider<JacocoReport>,
    androidTestVariantName: String,
    classExcludes: List<String>,
) {
    extensions.configure<ApplicationAndroidComponentsExtension> {
        // AGP enables unit tests only for testBuildType by default. These reports
        // always run debug unit tests, including when emulator tests target release.
        beforeVariants(selector().withName("playDebug")) { variant ->
            variant.hostTests[HostTestBuilder.UNIT_TEST_TYPE]?.enable = true
        }
        onVariants(selector().withName("playDebug")) { debugVariant ->
            unitCoverage.registerAndroidSuite(
                module = project,
                variant = debugVariant,
                classExcludes = classExcludes,
            )
            includeTestExecutionData(
                report = mergedReport,
                taskName = "testPlayDebugUnitTest",
                pattern = "**/*.exec",
            )
        }

        onVariants(selector().withName("play$androidTestVariantName")) { androidTestVariant ->
            val connectedTest = "connectedPlay${androidTestVariantName}AndroidTest"
            includeAndroidClasses(
                report = androidTestReport,
                variant = androidTestVariant,
                classExcludes = classExcludes,
            )
            includeTestExecutionData(
                report = androidTestReport,
                taskName = connectedTest,
                pattern = "**/*.ec",
            )
            includeAndroidClasses(
                report = mergedReport,
                variant = androidTestVariant,
                classExcludes = classExcludes,
            )
            includeTestExecutionData(
                report = mergedReport,
                taskName = connectedTest,
                pattern = "**/*.ec",
            )
        }
    }
}

/** Adds an Android library's debug classes and unit-test coverage to a report. */
private fun Project.configureAndroidLibraryCoverage(
    unitCoverage: UnitTestCoverageReports,
    classExcludes: List<String>,
) {
    val coverageVariants = extensions.getByType(JacocoLibraryVariants::class.java)
    coverageVariants.variants.all {
        unitCoverage.registerAndroidSuite(
            module = project,
            variant = this,
            classExcludes = classExcludes,
        ) {
            // Empty Android library test tasks may require resources supplied by the app.
            hasAndroidUnitTestSources()
        }
    }
}

/** Adds a JVM module's main classes and unit-test coverage to a report. */
private fun Project.configureJvmCoverage(
    unitCoverage: UnitTestCoverageReports,
    classExcludes: List<String>,
) {
    pluginManager.withPlugin("java") {
        val mainSourceSet = extensions.getByType(SourceSetContainer::class.java).named("main")
        val sources = mainSourceSet.map { it.allSource.srcDirs }
        val classes =
            mainSourceSet.map { sourceSet ->
                sourceSet.output.classesDirs.asFileTree
                    .matching { exclude(classExcludes) }
            }
        unitCoverage.registerSuite(
            module = project,
            taskName = "test",
            runtimeClasspath = "testRuntimeClasspath",
            sources = files(sources),
            classes = files(classes),
        )
    }
}

private fun Project.includeAndroidClasses(
    report: TaskProvider<JacocoReport>,
    variant: Variant,
    classExcludes: List<String>,
) {
    val classes =
        androidCoverageClasses(
            report = report,
            variant = variant,
            classExcludes = classExcludes,
        )
    report.configure {
        classDirectories.from(classes)
        sourceDirectories.from(variant.sources.java?.all, variant.sources.kotlin?.all)
    }
}

internal fun Project.androidCoverageClasses(
    report: TaskProvider<JacocoReport>,
    variant: Variant,
    classExcludes: List<String>,
): FileTree {
    val classInputs = objects.newInstance(AndroidCoverageInputs::class.java)
    val projectArtifacts = variant.artifacts.forScope(ScopedArtifacts.Scope.PROJECT)
    projectArtifacts.use(report).toGet(
        ScopedArtifact.CLASSES,
        { classInputs.jars },
        { classInputs.directories },
    )
    return classInputs.classes.matching { exclude(classExcludes) }
}

internal fun Project.includeTestExecutionData(
    report: TaskProvider<JacocoReport>,
    taskName: String,
    pattern: String,
    includeModule: () -> Boolean = { true },
) {
    val coverageProject = this
    report.configure {
        if (!includeModule()) return@configure
        val testTask = coverageProject.tasks.named(taskName)
        // Restrict coverage to the declared outputs
        val coverageFiles = coverageProject.files(testTask).asFileTree.matching { include(pattern) }
        executionData.from(coverageFiles)
        dependsOn(testTask)
        if (pattern == "**/*.exec") {
            runUnitTestsWithoutCaching(
                report = this,
                project = coverageProject,
                taskName = taskName,
            )
        }
    }
}

// Follow the app's runtime graph, including transitive project dependencies.
// This excludes build tooling such as lint rules and baseline-profile generators.
fun Project.runtimeProjectDependencies(configurationName: String): List<Project> =
    configurations[configurationName]
        .incoming.resolutionResult.allComponents
        .mapNotNull { it.id as? ProjectComponentIdentifier }
        .filter { it.build.buildPath == ":" && it.projectPath != path }
        .map { project(it.projectPath) }

fun runUnitTestsWithoutCaching(
    report: JacocoReport,
    project: Project,
    taskName: String,
) {
    val test = project.tasks.named<Test>(taskName).get()
    report.dependsOn(test)
    // Preserve the existing coverage policy: always rerun these tests, bypassing
    // up-to-date checks and the build cache. Configuration caching is unaffected.
    // TODO: Revisit whether coverage reports need fresh test executions.
    test.outputs.upToDateWhen { false }
    test.outputs.cacheIf { false }
}

private val Project.androidTestVariantName: String get() = extra["androidTestVariantName"] as String
