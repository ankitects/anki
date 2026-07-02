/*
 *  Copyright (c) 2026 David Allison <davidallisongithub@gmail.com>
 *
 *  This program is free software; you can redistribute it and/or modify it under
 *  the terms of the GNU General Public License as published by the Free Software
 *  Foundation; either version 3 of the License, or (at your option) any later
 *  version.
 *
 *  This program is distributed in the hope that it will be useful, but WITHOUT ANY
 *  WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 *  PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License along with
 *  this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import com.ichi2.anki.gradle.ReportOpener
import groovy.util.Node
import groovy.xml.XmlParser
import org.gradle.api.GradleException
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import java.io.FileNotFoundException
import java.util.Properties

apply(plugin = "jacoco")

val jacocoVersion: String =
    extensions
        .getByType(VersionCatalogsExtension::class.java)
        .named("libs")
        .findVersion("jacoco")
        .get()
        .requiredVersion

configure<JacocoPluginExtension> {
    toolVersion = jacocoVersion
}

fun getLocalProperties(): Properties {
    val propertiesFile = project.rootProject.file("local.properties")

    val properties = Properties()

    if (propertiesFile.exists()) {
        properties.load(propertiesFile.inputStream())
    }

    return properties
}

val openReportRequested = project.hasProperty("open-report")
val linuxHtmlCmd: String? = getLocalProperties()["linux-html-cmd"]?.toString()

tasks.withType<Test>().configureEach {
    val jacoco = the<JacocoTaskExtension>()
    jacoco.isIncludeNoLocationClasses = true
    jacoco.excludes = listOf("jdk.internal.*")
}

// source: https://medium.com/jamf-engineering/android-kotlin-code-coverage-with-jacoco-sonar-and-gradle-plugin-6-x-3933ed503a6e
val fileFilter =
    listOf(
        // android
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*Test*.*",
        "android/**/*.*",
        // kotlin
        "**/*MapperImpl*.*",
        "**/*\$ViewInjector*.*",
        "**/*\$ViewBinder*.*",
        "**/BuildConfig.*",
        "**/*Component*.*",
        "**/*BR*.*",
        "**/Manifest*.*",
        "**/*\$Lambda\$*.*",
        "**/*Companion*.*",
        "**/*Module*.*",
        "**/*Dagger*.*",
        "**/*Hilt*.*",
        "**/*MembersInjector*.*",
        "**/*_MembersInjector.class",
        "**/*_Factory*.*",
        "**/*_Provide*Factory*.*",
        "**/*Extensions*.*",
        // sealed and data classes
        "**/*\$Result.*",
        "**/*\$Result\$*.*",
    )

fun builtInKotlincClassDir(variant: String): String {
    // AGP9 built-in Kotlin compiler writes classes to
    // build/intermediates/built_in_kotlinc/<variant>/compile<Variant>Kotlin/classes
    return "intermediates/built_in_kotlinc/$variant/compile${variant.replaceFirstChar { it.uppercase() }}Kotlin/classes"
}

val flavorClassDir = builtInKotlincClassDir("play${rootProject.androidTestVariantName}")
val moduleClassDir = builtInKotlincClassDir(rootProject.androidTestVariantName.lowercase())

// Our merge report task
tasks.register<JacocoReport>("jacocoTestReport") {
    val htmlOutDir =
        project.layout.buildDirectory
            .dir("reports/jacoco/$name/html")
            .get()
            .asFile
    val openReport = openReportRequested
    val htmlCmd = linuxHtmlCmd

    doLast {
        ReportOpener.open(htmlOutDir, openReport, htmlCmd)
    }

    reports {
        xml.required = true
        html.outputLocation = htmlOutDir
    }

    val kotlinClasses =
        project.fileTree(
            mapOf(
                "dir" to project.layout.buildDirectory.dir(flavorClassDir),
                "excludes" to fileFilter,
            ),
        )
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(project.files(mainSrc))
    classDirectories.setFrom(project.files(kotlinClasses))
    executionData.setFrom(
        project.fileTree(
            mapOf(
                "dir" to project.layout.buildDirectory,
                "includes" to listOf("**/*.exec", "**/*.ec"),
            ),
        ),
    )
    dependsOn("testPlayDebugUnitTest")
    dependsOn("connectedPlay${rootProject.androidTestVariantName}AndroidTest")
}

// Android library modules (do not yet support flavors play/full)
val androidModulesToUnitTest = listOf(":api", ":common:android", ":libanki", ":compat")
// JVM modules: use 'test' task and 'classes/kotlin/main' class dir
val jvmModulesToUnitTest = listOf(":common")

// A unit-test only report task
tasks.register<JacocoReport>("jacocoUnitTestReport") {
    val report = this
    val htmlOutDir =
        layout.buildDirectory
            .dir("reports/jacoco/$name/html")
            .get()
            .asFile
    val openReport = openReportRequested
    val htmlCmd = linuxHtmlCmd

    doLast {
        ReportOpener.open(htmlOutDir, openReport, htmlCmd)
    }

    reports {
        xml.required = true
        html.outputLocation = htmlOutDir
    }

    includeUnitTestCoverage(report, getProject(), flavorClassDir)
    dependsOn("testPlayDebugUnitTest")

    for (module in androidModulesToUnitTest) {
        includeUnitTestCoverage(report, project(module), moduleClassDir)
        dependsOn("$module:testDebugUnitTest")
    }

    for (module in jvmModulesToUnitTest) {
        includeUnitTestCoverage(report, project(module), "classes/kotlin/main")
        dependsOn("$module:test")
    }
}

gradle.projectsEvaluated {
    // ensure test results aren't cached
    tasks.configureEach {
        if (name == "testPlayDebugUnitTest") {
            outputs.upToDateWhen { false }
            outputs.cacheIf { false }
        }
    }
    for (module in androidModulesToUnitTest) {
        project(module).tasks.findByName("testDebugUnitTest")?.let { task ->
            task.outputs.upToDateWhen { false }
            task.outputs.cacheIf { false }
        }
    }
    for (module in jvmModulesToUnitTest) {
        project(module).tasks.findByName("test")?.let { task ->
            task.outputs.upToDateWhen { false }
            task.outputs.cacheIf { false }
        }
    }
}

fun includeUnitTestCoverage(
    report: JacocoReport,
    project: Project,
    classDir: String,
) {
    report.sourceDirectories.from(project.files("${project.projectDir}/src/main/java"))
    report.classDirectories.from(
        project.files(
            project.fileTree(
                mapOf(
                    "dir" to project.layout.buildDirectory.dir(classDir),
                    "excludes" to fileFilter,
                ),
            ),
        ),
    )
    report.executionData.from(
        project.files(
            project.fileTree(
                mapOf(
                    "dir" to project.layout.buildDirectory,
                    "includes" to listOf("**/*.exec"),
                ),
            ),
        ),
    )
}

// A connected android tests only report task
tasks.register<JacocoReport>("jacocoAndroidTestReport") {
    val htmlOutDir =
        layout.buildDirectory
            .dir("reports/jacoco/$name/html")
            .get()
            .asFile
    val openReport = openReportRequested
    val htmlCmd = linuxHtmlCmd

    doLast {
        ReportOpener.open(htmlOutDir, openReport, htmlCmd)
    }

    reports {
        xml.required = true
        html.outputLocation = htmlOutDir
    }

    val kotlinClasses =
        project.fileTree(
            mapOf(
                "dir" to project.layout.buildDirectory.dir(flavorClassDir),
                "excludes" to fileFilter,
            ),
        )
    val mainSrc = "${project.projectDir}/src/main/java"

    sourceDirectories.setFrom(project.files(mainSrc))
    classDirectories.setFrom(project.files(kotlinClasses))
    executionData.setFrom(
        project.fileTree(
            mapOf(
                "dir" to project.layout.buildDirectory,
                "includes" to listOf("**/*.ec"),
            ),
        ),
    )
    dependsOn("connectedPlay${rootProject.androidTestVariantName}AndroidTest")
}

// Issue 16640 - some emulators run, but register zero coverage
tasks.register("assertNonzeroAndroidTestCoverage") {
    // Resolve the file at configuration time, which is Gradle Configuration Cache compatible
    val jacocoReportFile = layout.buildDirectory.file("reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml")
    doLast {
        val jacocoReport = jacocoReportFile.get().asFile

        if (!jacocoReport.exists()) {
            throw FileNotFoundException("jacocoAndroidTestReport.xml was not found after running jacocoAndroidTestReport")
        }

        val xmlParser = XmlParser()
        xmlParser.setFeature("http://apache.org/xml/features/disallow-doctype-decl", false)
        xmlParser.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)

        val reportRoot: Node = xmlParser.parse(jacocoReport)
        var hasCovered = false

        // https://github.com/jacoco/jacoco/blob/5aabb2eb60bbcd05df968005f1746ba19dcd5361/org.jacoco.report/src/org/jacoco/report/internal/xml/ReportElement.java#L190
        for (rawChild in reportRoot.children()) {
            val child = rawChild as Node
            if (child.name() != "counter") continue

            if (child.attribute("covered") == "0") {
                logger.warn(
                    "jacoco registered zero code coverage for counter type ${child.attribute("type")}",
                    null as Throwable?,
                )
            } else {
                hasCovered = true
            }
        }

        if (!hasCovered) {
            throw GradleException(
                "androidTest registered zero code coverage in jacocoAndroidTestReport.xml. Probably some incompatibilities in the toolchain.",
            )
        }
    }
}

afterEvaluate {
    tasks.named("jacocoAndroidTestReport").configure {
        finalizedBy("assertNonzeroAndroidTestCoverage")
    }
}

val Project.androidTestVariantName: String get() = extra["androidTestVariantName"] as String
