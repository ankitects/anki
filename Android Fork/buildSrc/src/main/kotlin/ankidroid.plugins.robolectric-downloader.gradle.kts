// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.tasks.WriteProperties

/*
 * Downloads android-all-instrumented dependencies into Gradle's cache for offline Robolectric.
 * Writes a properties file pointing to the cached jars, so worktrees do not need their own copies.
 *
 * Keep this as a convention plugin: applying a separate .gradle.kts script makes Android lint's
 * K2 build-script analyzer crash with `findFirCompiledSymbol`.
 *
 * Once applied to your gradle project, can be executed with ./gradlew robolectricSdkDownload
 */

// The general idea of this was borrowed from https://gist.github.com/xian/05c4f27da6d4156b9827842217c2cd5c
// I then modified it heavily to allow easier addition of new SDK versions
// The full implementation is from https://gist.github.com/simtel12/13ff3e57c37e78e468502b51ebb0f4f2

// List from: https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/DefaultSdkProvider.java
// This list will need to be updated for new Android SDK versions that come out.
// Note: the PREINSTRUMENTED_VERSION constant is what you put on the end of the artifact

// Only the versions currently used in AnkiDroid Robolectric tests are active, the rest are commented out
// When updating Robolectric, match these versions to DefaultSdkProvider and PREINSTRUMENTED_VERSION.
// Run `./gradlew jacocoUnitTestReport` to check that all SDKs used by the tests are included.
val robolectricAndroidSdkVersions =
    listOf(
//        mapOf("androidVersion" to "6.0.1_r3", "frameworkSdkBuildVersion" to "r1"),
        mapOf("androidVersion" to "7.0.0_r1", "frameworkSdkBuildVersion" to "r1"),
        mapOf("androidVersion" to "7.1.0_r7", "frameworkSdkBuildVersion" to "r1"),
//        mapOf("androidVersion" to "8.0.0_r4", "frameworkSdkBuildVersion" to "r1"),
        mapOf("androidVersion" to "8.1.0", "frameworkSdkBuildVersion" to "4611349"),
        mapOf("androidVersion" to "9", "frameworkSdkBuildVersion" to "4913185-2"),
        mapOf("androidVersion" to "10", "frameworkSdkBuildVersion" to "5803371"),
        mapOf("androidVersion" to "11", "frameworkSdkBuildVersion" to "6757853"),
        mapOf("androidVersion" to "12", "frameworkSdkBuildVersion" to "7732740"),
        mapOf("androidVersion" to "12.1", "frameworkSdkBuildVersion" to "8229987"),
        mapOf("androidVersion" to "13", "frameworkSdkBuildVersion" to "9030017"),
        mapOf("androidVersion" to "14", "frameworkSdkBuildVersion" to "10818077"),
        mapOf("androidVersion" to "15", "frameworkSdkBuildVersion" to "13954326"),
        // TODO: check TarArchiveInputStream.forEachEntry when this is updated
        mapOf("androidVersion" to "16", "frameworkSdkBuildVersion" to "13921718"), // current targetSdk
        mapOf("androidVersion" to "17", "frameworkSdkBuildVersion" to "15733970"),
    )

val robolectricSdkDownload =
    tasks.register<WriteProperties>("robolectricSdkDownload") {
        group = "Dependencies"
        description = "Downloads Robolectric SDKs and writes their cached paths for offline tests"
        destinationFile = layout.buildDirectory.file("robolectric-deps.properties")
    }

// Use separate configurations so Gradle retains every SDK version instead of selecting the newest.
robolectricAndroidSdkVersions.forEach { robolectricSdkVersion ->
    // the final part of this `-i<number>` comes from PREINSTRUMENTED_VERSION in upstream DefaultSdkProvider
    val version = "${robolectricSdkVersion["androidVersion"]}-robolectric-${robolectricSdkVersion["frameworkSdkBuildVersion"]}-i7"

    // Creating a configuration with a dependency allows Gradle to manage the actual resolution of
    // the jar file
    val sdkConfig = configurations.create(version)
    dependencies.add(version, "org.robolectric:android-all-instrumented:$version")

    robolectricSdkDownload.configure {
        inputs.files(sdkConfig).withPropertyName(version)
        property(
            "org.robolectric:android-all-instrumented:$version",
            sdkConfig.elements.map {
                it
                    .iterator()
                    .next()
                    .asFile.absolutePath
            },
        )
    }
}
