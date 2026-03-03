pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
    // Added automatically by Android Studio for Gradle Daemon JVM Criteria
    // https://developer.android.com/studio/releases/past-releases/as-panda-1-release-notes#daemon-jvm-criteria
    // Version catalog references are (mostly) unsupported in settings.gradle.kts
    // https://discuss.gradle.org/t/how-to-use-version-catalog-in-the-root-settings-gradle-kts-file/44603

    // https://github.com/gradle/foojay-toolchains
    // Changelog: https://plugins.gradle.org/plugin/org.gradle.toolchains.foojay-resolver-convention
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    // TODO enforce repositories declared here, currently it clashes with robolectricDownloader.gradle
    //  which uses a local maven repository
    // repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven(url = "https://jitpack.io")
    }
}

// alphabetical ordering rather than dependency-tree ordering to avoid bikeshedding
include(
    ":anki-common",
    ":api",
    ":AnkiDroid",
    ":common",
    ":common:android",
    ":compat",
    ":libanki",
    ":lint-rules",
    ":vbpd",
)