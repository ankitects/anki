plugins {
    id("ankidroid.jvm.library")
    `java-test-fixtures`
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
