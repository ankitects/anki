import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    // Use `id` to avoid classpath conflicts. Versions are pinned by buildSrc/.
    id("org.jetbrains.kotlin.jvm")
    `java-test-fixtures`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

dependencies {
    // #20852: JSON must be compile-time only: Bundling `org.json:json` into the APK ships a second
    // copy with a different field schema (vs the system version in `android.jar`)
    compileOnly(libs.json)
    testImplementation(libs.json)
    implementation(libs.androidx.annotation)
    implementation(libs.slf4j.api)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.hamcrest)
    testImplementation(libs.junit.platform.launcher)
    testImplementation(kotlin("test"))

    testFixturesImplementation(libs.hamcrest)
    testFixturesImplementation(libs.androidx.annotation)
    testFixturesImplementation(libs.slf4j.api)
}
