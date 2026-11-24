// SPDX-License-Identifier: GPL-3.0-or-later

import com.ichi2.anki.gradle.assertNonzeroAndroidTestCoverage
import com.ichi2.anki.gradle.configureJacocoAgent
import com.ichi2.anki.gradle.isCoverageEnabled
import com.ichi2.anki.gradle.registerJacocoCoverageReports
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.testing.Test
import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
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

// Toggle JaCoCo's unit test agents in all JaCoCo-enabled projects
val coverageEnabled = project.isCoverageEnabled()
rootProject.allprojects {
    pluginManager.withPlugin("jacoco") {
        tasks.withType<Test>().configureEach {
            extensions.configure<JacocoTaskExtension> {
                isEnabled = coverageEnabled
            }
        }
    }
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

configureJacocoAgent()

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

registerJacocoCoverageReports(
    fileFilter = fileFilter,
    openReportRequested = openReportRequested,
    linuxHtmlCmd = linuxHtmlCmd,
)

// Issue 16640 - some emulators run, but register zero coverage
tasks.register("assertNonzeroAndroidTestCoverage") {
    // Resolve the file at configuration time, which is Gradle Configuration Cache compatible
    val jacocoReportFile = layout.buildDirectory.file("reports/jacoco/jacocoAndroidTestReport/jacocoAndroidTestReport.xml")
    doLast {
        val jacocoReport = jacocoReportFile.get().asFile

        assertNonzeroAndroidTestCoverage(jacocoReport, logger)
    }
}

afterEvaluate {
    tasks.named("jacocoAndroidTestReport").configure {
        finalizedBy("assertNonzeroAndroidTestCoverage")
    }
}
