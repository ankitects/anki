import com.android.build.api.dsl.LibraryExtension
import com.ichi2.anki.gradle.addAnkiBackendDependencies

plugins {
    id("ankidroid.android.library")
    alias(libs.plugins.kotlin.serialization)
}

configure<LibraryExtension> {
    namespace = "com.ichi2.anki.libanki"
    testFixtures.enable = true
}

dependencies {
    // Project dependencies
    implementation(project(":common"))

    // Backend libraries
    addAnkiBackendDependencies(project)

    // JVM dependencies
    implementation(libs.jakewharton.timber)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    // Android interface dependencies
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.androidx.test.rules) // @SdkSuppress

    // test dependencies
    testImplementation(libs.hamcrest)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.json)

    // testFixtures dependencies
    testFixturesImplementation(project(":common"))
    testFixturesImplementation(libs.jakewharton.timber)
    testFixturesImplementation(libs.junit.vintage.engine)
    testFixturesImplementation(libs.kotlinx.coroutines.core)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
    testFixturesImplementation(libs.androidx.sqlite.framework)
}
