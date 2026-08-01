import com.android.build.api.dsl.TestExtension

plugins {
    // Use `id` to avoid classpath conflicts. Version pinned by buildSrc/.
    id("com.android.test")
    alias(libs.plugins.androidx.baselineprofile)
}

configure<TestExtension> {
    namespace = "com.ichi2.anki.baselineprofile"

    compileSdk =
        libs.versions.compileSdk
            .get()
            .toInt()

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        // Macrobenchmark + baseline profile generation require API 28+
        // even though the app may support a lower version. If the app's
        // minSdk ever rises above 28, follow it automatically.
        minSdk =
            maxOf(
                28,
                libs.versions.minSdk
                    .get()
                    .toInt(),
            )
        targetSdk =
            libs.versions.targetSdk
                .get()
                .toInt()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // AnkiDroid defines three product flavors (play, amazon, full) in the
        // `appStore` dimension. Rather than duplicate them here we target the
        // `play` flavor via `missingDimensionStrategy`, which keeps this module
        // flavor-free but still able to resolve `:AnkiDroid` variants.
        missingDimensionStrategy("appStore", "play")
    }

    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":AnkiDroid"

    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

baselineProfile {
    useConnectedDevices = true
}

apply(from = "../lint.gradle")

dependencies {
    implementation(libs.androidx.test.junit)
    implementation(libs.androidx.espresso.core)
    implementation(libs.androidx.uiautomator)
    implementation(libs.androidx.benchmark.macro.junit4)
}

androidComponents {
    onVariants { v ->
        val artifactsLoader = v.artifacts.getBuiltArtifactsLoader()
        // Pull the applicationId from the actual built APK instead of
        // hardcoding `com.ichi2.anki`. Most of us build with something like
        // `-PcustomSuffix=".bp"` so the benchmark APK installs next to our
        // real AnkiDroid (`com.ichi2.anki.bp`) rather than replacing it.
        // If we hardcoded the fallback, the benchmark would happily target
        // the production app and nuke your actual collection.
        v.instrumentationRunnerArguments.put(
            "targetAppId",
            v.testedApks.map {
                artifactsLoader.load(it)?.applicationId
                    ?: error(
                        "Could not resolve targetAppId from testedApks. " +
                            "Refusing to fall back to a default to avoid clobbering the production install.",
                    )
            },
        )
    }
}
