// SPDX-License-Identifier: GPL-3.0-or-later

// Apply after the Android app/library plugin. Gradle runs these aliases in every
// participating module: ./gradlew compareScreenshots -Pscreenshot
plugins {
    id("io.github.takahirom.roborazzi")
}

val screenshotRun = providers.gradleProperty("screenshot").isPresent
val variant = if (plugins.hasPlugin("com.android.application")) "PlayDebug" else "Debug"

tasks.register("compileScreenshotTests") {
    group = "verification"
    description = "Compiles $variant screenshot tests without running them."
    dependsOn("compile${variant}UnitTestSources")
}

listOf("record", "compare", "verify").forEach { mode ->
    tasks.register("${mode}Screenshots") {
        group = "verification"
        description = "Runs Roborazzi $mode for $variant (requires -Pscreenshot)."
        if (screenshotRun) {
            dependsOn("${mode}Roborazzi$variant")
        } else {
            doLast { throw GradleException("Screenshot tasks require -Pscreenshot") }
        }
    }
}

val cleanScreenshotDiffs =
    tasks.register<Delete>("cleanScreenshotDiffs") {
        // Keep baselines, including when running a filtered subset of tests.
        // Match generated filenames: a directory named `diffs` may contain baselines.
        // Clear cached copies too, so Roborazzi cannot restore obsolete comparisons.
        listOf("outputs/roborazzi", "intermediates/roborazzi").forEach { directory ->
            delete(
                fileTree(layout.buildDirectory.dir(directory)) {
                    include("**/*_compare.png", "**/*_actual.png")
                },
            )
        }
    }

val collectScreenshotDiffs =
    tasks.register<Sync>("collectScreenshotDiffs") {
        // Roborazzi declares this directory as a test output, including cached runs.
        from(layout.buildDirectory.dir("intermediates/roborazzi")) {
            include("**/*_compare.png")
        }
        into(layout.buildDirectory.dir("reports/screenshot-diffs"))
        includeEmptyDirs = false
        mustRunAfter("finalizeTestRoborazzi$variant")
    }

tasks.withType<Test>().configureEach {
    useJUnitPlatform {
        if (screenshotRun) {
            includeTags("com.ichi2.anki.ScreenshotTestCategory")
        } else {
            excludeTags("com.ichi2.anki.ScreenshotTestCategory")
        }
    }
    if (screenshotRun && name == "test${variant}UnitTest") {
        dependsOn(cleanScreenshotDiffs)
        // Keep comparisons available even when verification fails.
        finalizedBy(collectScreenshotDiffs)
    }
}
