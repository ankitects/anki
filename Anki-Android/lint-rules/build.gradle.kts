plugins {
    id("ankidroid.jvm.library")
}

dependencies {
    compileOnly(libs.android.lint.api)
    compileOnly(libs.android.lint)
    testImplementation(libs.hamcrest)
    testImplementation(libs.hamcrest.library)
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.android.lint.api)
    testImplementation(libs.android.lint)
    testImplementation(libs.android.lint.tests)
}
