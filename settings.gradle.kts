pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}
plugins {
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