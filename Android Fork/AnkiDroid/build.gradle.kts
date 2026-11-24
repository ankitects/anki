// Keep the existing AGP DSL/task APIs until the separate AGP DSL migration.
@file:Suppress("DEPRECATION")

import com.android.build.api.variant.FilterConfiguration.FilterType
import com.ichi2.anki.gradle.VersionCodeTasks
import com.ichi2.anki.gradle.WriteReleaseMetadata
import com.ichi2.anki.gradle.isCoverageEnabled
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import java.util.Properties
import java.util.regex.Pattern

plugins {
    // Gradle plugin portal
    alias(libs.plugins.tripletPlay)
    id("ankidroid.android.app")
    id("ankidroid.android.compose")
    id("ankidroid.plugins.jacoco")
    id("ankidroid.plugins.robolectric-downloader")
    alias(libs.plugins.androidx.baselineprofile)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.keeper)
    id("ankidroid.plugins.screenshots")
    id("idea")
}

repositories {
    google()
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

keeper {
    traceReferences {
        // Silence missing definitions
        arguments.set(listOf("--map-diagnostics:MissingDefinitionsDiagnostic", "error", "none"))
    }
}

baselineProfile {
    // Write generated profiles to `src/main/baselineProfiles/` instead of the
    // default per-variant `src/playRelease/generated/baselineProfiles/`. This
    // ensures the profile is consumed by ALL variants (play, full) and
    // ALL buildTypes (release, benchmark), not just `playRelease`.
    mergeIntoMain = true
}

idea {
    module {
        isDownloadJavadoc = System.getenv("CI") != "true"
        isDownloadSources = System.getenv("CI") != "true"
    }
}

val testReleaseBuild = rootProject.extra["testReleaseBuild"] as Boolean
val universalApkEnabled = rootProject.extra["universalApkEnabled"] as Boolean
val androidTestVariantName = rootProject.extra["androidTestVariantName"] as String

val homePath = System.getProperty("user.home")

/**
 * Calculates the current git hash, invalidates configuration cache if changed
 * @example edf739d95bad7b370a6ed4398d46723f8219b3cd
 * @see https://docs.gradle.org/current/userguide/configuration_cache_requirements.html#config_cache:requirements:external_processes
 */
// anki-time-fork: the app is built from a copy outside git, so the build passes the commit
val gitCommitHash =
    providers.gradleProperty("forkCommitHash").orElse(
        providers
            .exec {
                commandLine("git", "rev-parse", "HEAD")
            }.standardOutput.asText
            .map { it.trim() },
    )

/**
 * Epoch millis of the build. Displayed on the 'About' screen.
 *
 * CI passes the real time via -PbuildTime=<epoch> # example: 1776422957000
 *
 * This value is captured in the configuration cache, so it can be stale when the cache is reused.
 */
val buildTimeMillis =
    providers
        .gradleProperty("buildTime")
        .orElse(
            providers.provider {
                LocalDate
                    .now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli()
                    .toString()
            },
        )

android {
    namespace = "com.ichi2.anki"

    buildFeatures {
        buildConfig = true
        aidl = true
        viewBinding = true
        resValues = true
    }

    if (testReleaseBuild) {
        testBuildType = "release"
    } else {
        testBuildType = "debug"
    }

    defaultConfig {
        applicationId = "com.ichi2.anki"
        buildConfigField("Boolean", "CI", (System.getenv("CI") == "true").toString())
        buildConfigField("String", "ACRA_URL", "\"https://ankidroid.org/acra/report\"")
        buildConfigField("String", "BACKEND_VERSION", "\"${libs.versions.ankiBackend.get()}\"")
        buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "false")
        buildConfigField("String", "GIT_COMMIT_HASH", "\"${gitCommitHash.get()}\"")
        buildConfigField("long", "BUILD_TIME", buildTimeMillis.get())
        // whether UI elements linking to external donation pages are shown ('play' hides them)
        buildConfigField("Boolean", "SHOW_DONATE_LINKS", "true")
        resValue("string", "app_name", "AnkiDroid")

        // The version number is of the form:
        // <major>.<minor>.<maintenance>[dev|alpha<build>|beta<build>|]
        // The <build> is only present for alpha and beta releases (e.g., 2.0.4alpha2 or 2.0.4beta4), developer builds do
        // not have a build number (e.g., 2.0.4dev) and official releases only have three components (e.g., 2.0.4).
        //
        // The version code is derived from the version name as follows:
        // AbbCCtDD
        // A: 1-digit decimal number representing the major version
        // bb: 2-digit decimal number representing the minor version
        // CC: 2-digit decimal number representing the maintenance version
        // t: 1-digit decimal number representing the type of the build
        // 0: developer build
        // 1: alpha release
        // 2: beta release
        // 3: public release
        // DD: 2-digit decimal number representing the build
        // 00 for internal builds and public releases
        // alpha/beta build number for alpha/beta releases
        //
        // This ensures the correct ordering between the various types of releases (dev < alpha < beta < release) which is
        // needed for upgrades to be offered correctly.
        versionCode = 22600115
        // If you change this to a new version, you probably also want to update .gradle/workflows/milestone.yml for the new version...
        versionName = "2.26.0alpha15"
        // anki-time-fork: the release workflow passes these so each build installs as an update
        // of the previous one (a higher versionCode), and crash reports of the fork do not go to
        // AnkiDroid's server. See "Android Fork/fork/README.md".
        project.findProperty("forkVersionCode")?.let { versionCode = it.toString().toInt() }
        project.findProperty("forkVersionName")?.let { versionName = it.toString() }
        project.findProperty("forkAcraUrl")?.let {
            buildConfigField("String", "ACRA_URL", "\"$it\"")
        }
        testApplicationId = "com.ichi2.anki.tests"
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "com.ichi2.testutils.NewCollectionPathTestRunner"

        // GA4 Measurement Protocol api_secret. Sourced from `local.properties`
        // (ANALYTICS_API_KEY) or the ANALYTICS_API_KEY env var; falls back to
        // a dummy value so contributor builds still compile.
        //
        // The dummy key is rejected by GA's ingest, so analytics requests from
        // unsigned/local builds go nowhere this means `setDevMode()` (100%
        // sampling) has no observable effect on a build without the real key.
        //
        // Google documents the api_secret as private, and the Measurement Protocol
        // as intended for trusted/server-side use:
        // https://developers.google.com/analytics/devguides/collection/protocol/ga4/sending-events#api_secret
        // Shipping it in the APK means anyone can extract it and post junk events to
        // the property, which would corrupt our reporting. We accept that: there is no
        // server-side path for an offline-first app, the property holds no user data,
        // and a polluted property can be rotated by issuing a new secret.
        val properties = Properties()
        val localPropsFile = project.rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            properties.load(localPropsFile.inputStream())
        }

        val apiKey =
            properties.getProperty("ANALYTICS_API_KEY")?.takeIf { it.isNotEmpty() }
                ?: System.getenv("ANALYTICS_API_KEY")?.takeIf { it.isNotEmpty() } ?: "DUMMY_API_XXX"
        // Keep credentials out of BuildConfig: ACRA collects its fields through reflection.
        resValue("string", "ga_api_secret", apiKey)
    }
    signingConfigs {
        create("release") {
            val keystorePath = System.getenv("KEYSTOREPATH")
            if (keystorePath != null && !keystorePath.trim().isEmpty()) {
                storeFile = file(keystorePath)
                storePassword = System.getenv("KEYSTOREPWD")?.takeIf { it.isNotEmpty() } ?: System.getenv("KSTOREPWD")
                keyAlias = System.getenv("KEYALIAS")
                keyPassword = System.getenv("KEYPWD")
            } else {
                storeFile = file("$rootDir/tools/fallback-release-keystore.jks")
                storePassword = "Test@123"
                keyAlias = "my-key"
                keyPassword = "Test@123"
            }
        }
    }
    buildTypes {
        named("debug") {
            versionNameSuffix = "-debug"
            isDebuggable = true
            applicationIdSuffix = ".debug"
            splits.abi.isUniversalApk = true // Build universal APK for debug always
            // Check Crash Reports page on developer wiki for info on ACRA testing
            // buildConfigField "String", "ACRA_URL", '"https://918f7f55-f238-436c-b34f-c8b5f1331fe5-bluemix.cloudant.com/acra-ankidroid/_design/acra-storage/_update/report"'
            val localProperties = Properties()
            if (project.rootProject.file("local.properties").exists()) {
                localProperties.load(project.rootProject.file("local.properties").inputStream())
                // not profiled: optimization for build times
                if (localProperties["enable_languages"] == "false") {
                    android.defaultConfig.resourceConfigurations += "en"
                }
                // allow disabling leak canary
                if (localProperties["enable_leak_canary"] != null) {
                    buildConfigField("Boolean", "ENABLE_LEAK_CANARY", localProperties.getProperty("enable_leak_canary"))
                } else {
                    buildConfigField("Boolean", "ENABLE_LEAK_CANARY", "true")
                }
            }

            // Issue 6009: allow disabling JaCoCo to speed up debug builds.
            // A Gradle property also allows the preference to be shared across worktrees.
            isTestCoverageEnabled = project.isCoverageEnabled()

            // make the icon red if in debug mode
            resValue("color", "anki_foreground_icon_color_0", "#FFFF0000")
            resValue("color", "anki_foreground_icon_color_1", "#FFFF0000")
        }
        named("release") {
            isTestCoverageEnabled = testReleaseBuild
            isMinifyEnabled = System.getenv("MINIFY_ENABLED").let { if (!it.isNullOrEmpty()) it != "false" else true }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            testProguardFile("proguard-test-rules.pro")
            splits.abi.isUniversalApk = universalApkEnabled // Build universal APK for release with `-Duniversal-apk=true`
            signingConfig = signingConfigs.getByName("release")

            // syntax: assembleRelease -PcustomSuffix="suffix" -PcustomName="New name"
            if (project.hasProperty("customSuffix")) {
                // the suffix needs a '.' at the start
                applicationIdSuffix = project.property("customSuffix").toString().replaceFirst(Regex("^\\.*"), ".")
            }
            if (project.hasProperty("customName")) {
                resValue("string", "app_name", project.property("customName").toString())
            }

            resValue("color", "anki_foreground_icon_color_0", "#FF29B6F6")
            resValue("color", "anki_foreground_icon_color_1", "#FF0288D1")
        }

        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
        }
    }

    /*
     * Product Flavors are used for Google Play Store restrictions.
     */
    flavorDimensions += "appStore"
    productFlavors {
        create("play") {
            isDefault = true
            dimension = "appStore"
            // Google Play no longer considers Open Collective to be acceptable for
            // a 'tax-exempt' link. Temporarily remove our links
            buildConfigField("Boolean", "SHOW_DONATE_LINKS", "false")
        }
        // A 'full' build has no restrictions on storage/camera. Distributed on GitHub/F-Droid
        create("full") {
            dimension = "appStore"
        }
    }

    /**
     * Set this to true to create five separate APKs instead of one:
     *   - 2 APKs that only work on ARM/ARM64 devices
     *   - 2 APKs that only works on x86/x86_64 devices
     *   - a universal APK that works on all devices
     * The advantage is the size of most APKs is reduced by about 2.5MB.
     * Upload all the APKs to the Play Store and people will download
     * the correct one based on the CPU architecture of their device.
     */
    val enableSeparateBuildPerCPUArchitecture = true

    /**
     * Restricts the build to a single CPU architecture, e.g. `-PabiFilter=arm64-v8a`.
     * Allows F-Droid to build one APK per architecture without patching this file (#21399).
     */
    val abiFilter = providers.gradleProperty("abiFilter").getOrNull()
    val supportedAbis = listOf("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
    if (abiFilter != null && !supportedAbis.contains(abiFilter)) {
        throw GradleException("Unsupported -PabiFilter value '$abiFilter'. Expected one of: $supportedAbis")
    }

    splits {
        abi {
            reset()
            isEnable = enableSeparateBuildPerCPUArchitecture
            // universalApk enableUniversalApk  // set in debug + release config blocks above
            if (abiFilter != null) {
                include(abiFilter)
            } else {
                include("armeabi-v7a", "x86", "arm64-v8a", "x86_64")
            }
        }
    }
    testOptions {
        animationsDisabled = true
        unitTests {
            all { test ->
                // Profile coverage checks must rerun when the generated profiles change.
                test.inputs.dir("src/main/generated/baselineProfiles")
                test.useJUnitPlatform {
                    // ./gradlew testFullDebugUnitTest -Pscreenshot
                    if (project.hasProperty("screenshot")) {
                        // ./gradlew :AnkiDroid:recordRoborazziPlayDebug -Pscreenshot -Ptheme=black -Pdevice=tablet,foldable
                        val theme = project.findProperty("theme")?.takeIf { it.toString().isNotEmpty() } ?: "light"
                        val device = project.findProperty("device")?.takeIf { it.toString().isNotEmpty() } ?: "phone"
                        val validateInput = { input: Any, validOptions: List<String> ->
                            val requested =
                                Pattern
                                    .compile(
                                        ",",
                                    ).split(input.toString())
                                    .map { it.trim().lowercase(Locale.getDefault()) }
                            val invalid = requested - validOptions.toSet()
                            if (!invalid.isEmpty()) {
                                throw GradleException(
                                    "Invalid param: '${invalid.joinToString(", ")}'. Valid options: ${validOptions.joinToString(", ")}.",
                                )
                            }
                        }
                        validateInput(theme, listOf("light", "plain", "dark", "black", "eink", "eink_night", "all"))
                        validateInput(device, listOf("phone", "tablet", "foldable", "desktop", "all"))

                        test.systemProperty("screenshot.theme", theme)
                        test.systemProperty("screenshot.device", device)
                        test.systemProperty("screenshot.accessibilityScan", project.hasProperty("accessibilityScan"))
                    }
                    // Ensures that no EmptyApplication test relies on AnkiDroidApp
                    //  by only running EmptyApplication tests
                    // ./gradlew testFullDebugUnitTest -PemptyApplication
                    if (project.hasProperty("emptyApplication")) {
                        includeTags("com.ichi2.anki.EmptyApplicationCategory")
                    }
                }
            }
        }
    }

    // https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures
    // https://developer.android.com/build/releases/agp-7-2-0-release-notes#test-fixtures
    // ⚠️ There was an in-IDE warning: "Kotlin is not configured" when editing the testFixtures
    // files. I ended up ignoring the warning after the 'Configure' button in Android Studio
    // added dependencies but didn't fix the issue.
    testFixtures {
        enable = true
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }

    packagingOptions {
        resources {
            excludes += listOf("META-INF/DEPENDENCIES")
        }
    }
}

androidComponents.onVariants(androidComponents.selector().withBuildType("release")) { variant ->
    // We want the same version stream for all ABIs in debug but for release we can split them
    val versionCodes = mapOf("armeabi-v7a" to 1, "x86" to 2, "arm64-v8a" to 3, "x86_64" to 4)
    variant.outputs.forEach { output ->
        // Universal APKs keep their base version code; return early
        val abi = output.filters.find { it.filterType == FilterType.ABI }?.identifier ?: return@forEach

        // For each separate APK per architecture, set a unique version code as described here:
        // https://developer.android.com/studio/build/configure-apk-splits.html
        //  From: https://developer.android.com/studio/publish/versioning#appversioning
        //  "Warning: The greatest value Google Play allows for versionCode is 2100000000"
        //  AnkiDroid versionCodes have a budget 8 digits (through AnkiDroid 9)
        //  This style does ABI version code ranges with the 9th digit as 0-4.
        //  This consumes ~20% of the version range space, w/50 years of versioning at our major-version pace
        output.versionCode.set(
            // ex:  321200106 = 3 * 100000000 + 21200106
            versionCodes.getValue(abi) * 100000000 + output.versionCode.get(),
        )
    }
}

// register release tasks using the finalized version configuration.
androidComponents.finalizeDsl { androidDsl ->
    // output versionName etc... to `build/outputs/release-metadata.json` for use in CI
    tasks.register<WriteReleaseMetadata>("writeReleaseMetadata") {
        group = "release"
        description = "Writes the configured release version and type as JSON."
        versionName.set(androidDsl.defaultConfig.versionName)
        versionCode.set(androidDsl.defaultConfig.versionCode)
    }

    // register `validateVersionCode` with the finalized version code.
    val validation = VersionCodeTasks.registerValidationTask(project, androidDsl.defaultConfig.versionCode)
    // handle all 'publish' calls, even if run directly
    tasks.matching { it.name.startsWith("publish") }.configureEach {
        dependsOn(validation)
    }
}

play {
    serviceAccountCredentials.set(file("$homePath/src/AnkiDroid-GCP-Publish-Credentials.json"))
    track.set("alpha")

    // any time we bump minSdk we want Play Store to retain the old artifacts by version code,
    // so that they remain available for older devices
    retain {
        artifacts.set(
            listOf(
                20700300L, // (2.7, minSdk 10, universal APK)
                20804300L, // (2.8.4, minSdk 10, universal APK)
                21004300L, // (2.10.4, minSdk 15, universal APK)
                // release-2.14 minSdk 16: missing and not re-publishable, see issue 17791
                121603300L, // (2.16.3, minSdk 21, ABI armeabi-v7a)
                221603300L, // (2.16.3, minSdk 21, ABI x86)
                321603300L, // (2.16.3, minSdk 21, ABI arm64-v8a)
                421603300L, // (2.16.3, minSdk 21, ABI x86_64)
                121905300L, // (2.19.5, minSdk 23, ABI armeabi-v7a)
                221905300L, // (2.19.5, minSdk 23, ABI x86)
                321905300L, // (2.19.5, minSdk 23, ABI arm64-v8a)
                421905300L, // (2.19.5, minSdk 23, ABI x86_64)
            ),
        )
    }

    // If you retain APKs in a release with different names as we do above,
    // the plugin + Play Store has no idea how to name the release except by date.
    // release name is developer only, but sane names really help, so set one
    releaseName.set(android.defaultConfig.versionName)
}

// Install Git pre-commit hook for Ktlint
// Resolve via git so worktrees (where `.git` is a file) are handled correctly.
val gitHooksDir =
    providers
        .exec {
            workingDir = rootProject.rootDir
            commandLine("git", "rev-parse", "--git-path", "hooks")
        }.standardOutput.asText
        .map {
            rootProject.rootDir
                .toPath()
                .resolve(it.trim())
                .toFile()
        }

tasks.register<Copy>("installGitHook") {
    from(File(rootProject.rootDir, "pre-commit"))
    into(gitHooksDir)
    filePermissions {
        user {
            read = true
            write = true
            execute = true
        }
    }
}
// to run manually: `./gradlew installGitHook`
// anki-time-fork: not run on each build. The git repository here is the desktop fork's, whose
// hooks this would replace, and the build copies the app outside git.

// Fail fast when an emulator's primary user (user 0) is still locked after boot.
//
// Purpose: detect Play / Google-apis AVDs that have an account with a startup PIN or
// screen lock. Those report sys.boot_completed while user 0 stays RUNNING_LOCKED, so
// credential-encrypted storage is unavailable, AnkiDroidApp.onCreate crashes reading
// preferences, and the connected suite reports a confusing zero-test "success" after
// minutes of work. This check stops that before install.
//
// Scope:
// - Only user 0 (secondary profiles being locked must not fail the build).
// - Device set and selector precedence exactly match AGP connected tests:
//   task --serial, connectedCheck --serial, ANDROID_SERIAL, then every online device.
abstract class AssertDevicesUnlocked : DefaultTask() {
    @get:InputFile
    abstract val adbExecutable: RegularFileProperty

    /** --serial values supplied directly to connectedPlay<Variant>AndroidTest. */
    @get:Input
    abstract val directSerials: ListProperty<String>

    /** --serial values inherited from connectedCheck. */
    @get:Input
    abstract val connectedCheckSerials: ListProperty<String>

    /** ANDROID_SERIAL, tracked as task input for configuration-cache correctness. */
    @get:Input
    @get:Optional
    abstract val androidSerialEnv: Property<String>

    @get:javax.inject.Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun assertDevicesUnlocked() {
        val adb = adbExecutable.get().asFile.absolutePath
        val selection =
            selectSerials(
                directSerials.isPresent,
                directSerials.getOrElse(emptyList()),
                connectedCheckSerials.getOrElse(emptyList()),
                androidSerialEnv.getOrNull(),
            )
        val onlineSerials = mutableListOf<String>()
        for (line in adbOutput(adb, "devices").lines()) {
            if (!Regex("""\S+\tdevice""").matches(line)) {
                continue
            }
            onlineSerials.add(line.split("\t")[0])
        }

        if (onlineSerials.isEmpty()) {
            throw GradleException("No online devices found.")
        }

        var serialsToCheck: List<String> = onlineSerials
        if (selection.serials != null) {
            // Match AGP's intended multiset behavior for task-option lists: each online device
            // consumes one requested serial. Repeating the same --serial twice therefore leaves
            // one unmatched and fails.
            //
            // AGP 9.4 implements that by mutating targetSerials in
            // DeviceProviderInstrumentTestTask.getFilteredDevices(). That list comes from
            // ListProperty.get() (an ImmutableList), so connectedPlay*AndroidTest --serial
            // currently crashes with UnsupportedOperationException at ImmutableCollection.remove
            // before tests run. Reproduced on this repo with and without this preflight; no
            // public Issue Tracker ID found as of 2026-09-22. ANDROID_SERIAL still works because
            // that path never calls getFilteredDevices(). This preflight copies into an ArrayList
            // so we do not inherit the same crash.
            val unmatched = ArrayList(selection.serials)
            for (onlineSerial in onlineSerials) {
                unmatched.remove(onlineSerial)
            }
            if (unmatched.isNotEmpty()) {
                throw GradleException(
                    selection.source + " lists device(s) not connected (or not in 'device' state): " +
                        unmatched.joinToString(", ") + ". Connected: " + onlineSerials.joinToString(", ") + ".",
                )
            }
            serialsToCheck = selection.serials.distinct()
        }

        logger.info("Checking user-0 unlock state on $serialsToCheck selected by ${selection.source}")
        val locked = mutableListOf<String>()
        for (serial in serialsToCheck) {
            if (isUser0Locked(adbOutput(adb, "-s", serial, "shell", "dumpsys", "user"))) {
                locked.add(serial)
            }
        }
        if (locked.isNotEmpty()) {
            throw GradleException(
                "Device(s) " + locked.joinToString(", ") + " have booted but user 0 is still locked, so every " +
                    "test would fail in Application.onCreate. Unlock the screen (a Play AVD with an " +
                    "account + startup PIN stays locked until the PIN is entered), or select an unlocked " +
                    "device with --serial / ANDROID_SERIAL.",
            )
        }
    }

    data class SerialSelection(
        val source: String,
        val serials: List<String>?,
    )

    /**
     * Select serials using AGP DeviceProviderInstrumentTestTask.getTargetSerials() + run().
     *
     * Presence of the direct --serial ListProperty wins even when the list is empty: AGP then
     * skips connectedCheck --serial and consults ANDROID_SERIAL only because the target list is
     * empty. Groovy-truth on the list contents would wrongly fall through to connectedCheck.
     * ANDROID_SERIAL is Splitter.on(',').split with no trim (ConnectedDeviceProvider).
     * A null serial list means AGP will run against every online device.
     */
    fun selectSerials(
        directSerialsPresent: Boolean,
        directSerials: List<String>,
        connectedCheckSerials: List<String>,
        androidSerialEnv: String?,
    ): SerialSelection {
        val targetSerials = if (directSerialsPresent) directSerials else connectedCheckSerials
        val source = if (directSerialsPresent) "Task --serial" else "connectedCheck --serial"
        if (targetSerials.isNotEmpty()) {
            return SerialSelection(source, targetSerials)
        }
        if (androidSerialEnv != null && !androidSerialEnv.isEmpty()) {
            // AGP uses Sets.newHashSet(Splitter.on(',').split(androidSerialEnv)).
            val serials = androidSerialEnv.split(",").distinct()
            return SerialSelection("ANDROID_SERIAL", serials)
        }
        return SerialSelection("connected devices", null)
    }

    /**
     * True when dumpsys user reports user 0 as locked.
     * Handles both modern dumps (`Started users state: [0=RUNNING_UNLOCKED]`) and Android 11
     * dumps (`Started users state: {0=1, 10=3}`), where UserState.STATE_RUNNING_LOCKED = 1 and
     * STATE_RUNNING_UNLOCKED = 3 (frameworks/base/.../am/UserState.java). Match the user-0 entry
     * exactly so a locked secondary profile (e.g. 10=RUNNING_LOCKED / 10=1) cannot false-positive,
     * including via the "0=RUNNING_LOCKED" substring inside "10=RUNNING_LOCKED".
     */
    fun isUser0Locked(dumpsysUser: String): Boolean {
        val matcher = Regex("""Started users state: [\[{]([^\]}]*)[\]}]""").find(dumpsysUser)
        if (matcher == null) {
            return false
        }
        for (entry in matcher.groupValues[1].split(",")) {
            val parts = entry.trim().split("=", limit = 2)
            if (parts.size != 2 || parts[0] != "0") {
                continue
            }
            val state = parts[1]
            // Numeric (API 30 dumps) or named (newer dumps). STATE_RUNNING_LOCKED = 1.
            return state == "RUNNING_LOCKED" || state == "1"
        }
        return false
    }

    private fun adbOutput(
        adb: String,
        vararg args: String,
    ): String {
        val output = ByteArrayOutputStream()
        execOperations.exec {
            commandLine(adb, *args)
            standardOutput = output
        }
        return output.toString()
    }
}
tasks.register<AssertDevicesUnlocked>("assertDevicesUnlocked") {
    adbExecutable.set(androidComponents.sdkComponents.adb)
    // No convention([]) on the --serial lists: AGP getTargetSerials() keys off isPresent(),
    // and an empty convention would make the direct property look intentionally set.
    androidSerialEnv.set(providers.environmentVariable("ANDROID_SERIAL"))
    // Device state is not an input Gradle can track, so never consider this task up to date
    outputs.upToDateWhen { false }
}

// Issue 11078 - some emulators run, but run zero tests, and still report success
tasks.register("assertNonzeroAndroidTests") {
    // Resolve the directory at configuration time, which is Gradle Configuration Cache compatible.
    // The build type segment must track the variant the connected test task runs under, so it is
    // derived from the same property that names that task.
    val buildType = androidTestVariantName.lowercase(Locale.ROOT)
    val resultsDir = layout.buildDirectory.dir("outputs/androidTest-results/connected/$buildType/flavors/play")
    doLast {
        // androidTest currently creates one .xml file per emulator with aggregate results in this dir
        val folder = resultsDir.get().asFile
        val listOfFiles = folder.listFiles { _, f -> Regex(".*.xml").matches(f) }
        // No results at all is the same failure this guard exists to catch, and it is how the guard
        // itself goes unnoticed if the results layout moves again
        if (listOfFiles.isNullOrEmpty()) {
            throw GradleException(
                "No androidTest result files in " + folder + " - the connected test run reported success without producing results.",
            )
        }
        for (file in listOfFiles) {
            // The root element carries the aggregate count for one device, across any number of
            // nested per-class <testsuite> elements
            val declaredTests =
                groovy.xml
                    .XmlSlurper()
                    .parse(file)
                    .getProperty("@tests")
                    .toString()
            if (declaredTests.trim().toIntOrNull() == null) {
                throw GradleException("Unable to determine count of tests executed for " + file.name + ". Results format changed?")
            }
            if (declaredTests.trim().toInt() == 0) {
                throw GradleException(
                    "androidTest executed 0 tests for " + file.name + " - Probably a bug with the emulator. Try another image.",
                )
            }
        }
    }
}
afterEvaluate {
    val connectedTest =
        tasks
            .named<com.android.build.gradle.internal.tasks.DeviceProviderInstrumentTestTask>(
                "connectedPlay${androidTestVariantName}AndroidTest",
            ).get()
    // Keep the preflight device set in lockstep with AGP's connected-test selection. The nested
    // properties include task --serial and connectedCheck --serial after Gradle parses command-line
    // task options; AGP applies the same precedence in getTargetSerials().
    //
    // Reading these properties does not cause AGP's --serial crash. That crash is inside AGP 9.4
    // DeviceProviderInstrumentTestTask.getFilteredDevices(), which calls List.remove on the
    // ImmutableList returned by ListProperty.get(). Reproduced without this wiring; no public
    // tracker ID found as of 2026-09-22. Use ANDROID_SERIAL until AGP copies the list first.
    tasks.named<AssertDevicesUnlocked>("assertDevicesUnlocked").configure {
        directSerials.set(connectedTest.testRunnerFactory.deviceSerialValues)
        connectedCheckSerials.set(connectedTest.testRunnerFactory.connectedCheckDeviceSerials)
    }
    connectedTest.dependsOn("assertDevicesUnlocked")
    connectedTest.finalizedBy("assertNonzeroAndroidTests")

    // AGP bug: when testFixtures is enabled on an application module, the androidTest R8 task's
    // TESTED_CODE scope only includes the app's dependency artifacts, not the app's own compiled
    // classes. This causes R8 to report "Missing class" errors for all app classes referenced from
    // androidTest code. Work around by adding the app classes as referenced (library) input.
    if (testReleaseBuild) {
        tasks.named<com.android.build.gradle.internal.tasks.R8Task>("minifyPlayReleaseAndroidTestWithR8").configure {
            referencedClasses.from(
                files("build/intermediates/compile_app_classes_jar/playRelease/bundlePlayReleaseClassesToCompileJar/classes.jar"),
            )
        }
    }
}

android {
    lint {
        // Analyze project-local dependencies (:common:android etc.) as part of this
        // module's lint run.
        // Required for whole-app UnusedResources and StringFormatMatches coverage
        checkDependencies = true
    }
}

configurations.matching { it.name.endsWith("CompileClasspath") || it.name.endsWith("RuntimeClasspath") }.configureEach {
    resolutionStrategy {
        // Keep app dependencies such as Timber on our annotations version. Changing Kotlin's
        // compiler classpath creates an extra compiler daemon and increases peak memory use.
        force(libs.jetbrains.annotations)
    }
}

dependencies {
    api(project(":api"))
    implementation(libs.androidx.work.runtime)
    lintChecks(project(":lint-rules"))
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    compileOnly(libs.jetbrains.annotations)
    compileOnly(libs.auto.service.annotations)
    annotationProcessor(libs.auto.service)

    // modules
    implementation(project(":anki-common"))
    implementation(project(":common"))
    implementation(project(":common:android"))
    implementation(project(":compat"))
    implementation(project(":feature:browse"))
    implementation(project(":libanki"))
    implementation(project(":note-editor"))
    implementation(project(":vbpd"))
    "baselineProfile"(project(":baselineprofile"))
    implementation(project(":widgets"))

    implementation(libs.google.analytics.kt)

    implementation(libs.androidx.activity)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.concurrent.futures.ktx)
    implementation(libs.androidx.core)
    implementation(libs.androidx.draganddrop)
    implementation(libs.androidx.exifinterface)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.preference.ktx)
    implementation(libs.androidx.profileinstaller)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.sqlite.framework)
    implementation(libs.androidx.swiperefreshlayout)
    implementation(libs.androidx.viewpager2)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.webkit)
    implementation(libs.google.material)
    implementation(libs.android.image.cropper)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.seismic)
    implementation(libs.process.phoenix)

    debugImplementation(libs.androidx.fragment.testing.manifest)

    // Backend libraries

    implementation(libs.protobuf.kotlin.lite) // This is required when loading from a file

    // TODO: migrate to `addAnkiBackendDependencies(...)` when this is a gradle.kts file
    val localProperties = Properties()
    if (project.rootProject.file("local.properties").exists()) {
        localProperties.load(project.rootProject.file("local.properties").inputStream())
    }
    if (localProperties["local_backend"] == "true") {
        // Local artifacts have no Maven metadata, so both the core JAR and Android AAR are needed.
        implementation(
            files(
                rootProject.file("../Anki-Android-Backend/rsdroid/build/libs/rsdroid.jar"),
                rootProject.file("../Anki-Android-Backend/rsdroid-android/build/outputs/aar/rsdroid-android-release.aar"),
            ),
        )
        testImplementation(files(rootProject.file("../Anki-Android-Backend/rsdroid-testing/build/libs/rsdroid-testing.jar")))
    } else {
        implementation(libs.ankiBackend.backend)
        testImplementation(libs.ankiBackend.testing)
    }

    // May need a resolution strategy for support libs to our versions
    implementation(libs.acra.limiter)
    implementation(libs.acra.toast)
    implementation(libs.acra.dialog)
    implementation(libs.acra.http)

    implementation(libs.commons.compress)
    implementation(libs.commons.io) // FileUtils.contentEquals
    implementation(libs.okhttp)
    implementation(libs.slf4j.api)
    implementation(libs.jakewharton.timber)
    implementation(libs.jsoup)
    implementation(libs.java.semver) // For AnkiDroid JS API Versioning
    implementation(libs.drakeet.drawer)
    implementation(libs.skydoves.colorpickerview)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.test)
    implementation(libs.search.preference)

    // Cannot use debugImplementation since classes need to be imported in AnkiDroidApp
    // and there's no no-op version for release build. Usage has been disabled for release
    // build via AnkiDroidApp.
    implementation(libs.leakcanary.android)

    testImplementation(testFixtures(project(":libanki")))
    testImplementation(testFixtures(project(":common")))
    testImplementation(testFixtures(project(":common:android")))
    testImplementation(testFixtures(project(":compat")))
    testImplementation(testFixtures(project(":anki-common")))
    androidTestImplementation(testFixtures(project(":anki-common")))

    // A path for a testing library which provide Parameterized Test
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.junit.vintage.engine)
    testImplementation(libs.mockito.inline)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.hamcrest)
    testImplementation(libs.test.parameter.injector)
    // ankidroid.plugins.robolectric-downloader may need a new SDK jar entry
    // when Robolectric or targetSdk changes. See the instructions in that plugin.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.arch.core.testing)
    testImplementation(libs.androidx.test.junit)
    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.test.junit5)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.androidx.fragment.testing)
    // in a JvmTest we need org.json.JSONObject to not be mocked
    testImplementation(libs.json)
    testImplementation(libs.ivanshafran.shared.preferences.mock)
    testImplementation(libs.androidx.test.runner)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.espresso.contrib) {
        exclude(module = "protobuf-lite")
    }
    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.accessibility.check)
    testImplementation(libs.roborazzi.rule)
    // for testing flows
    testImplementation(libs.cashapp.turbine)

    androidTestImplementation(testFixtures(project(":anki-common")))

    // May need a resolution strategy for support libs to our versions
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.espresso.contrib) {
        exclude(module = "protobuf-lite")
    }
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    androidTestImplementation(libs.kotlin.test)
    androidTestImplementation(libs.kotlin.test.junit)
    androidTestImplementation(libs.androidx.fragment.testing)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.exoplayer.dash)
    implementation(libs.androidx.media3.ui)

    // ---- testFixtures setup ----
    testFixturesImplementation(libs.kotlin.stdlib)
    testFixturesImplementation(libs.jakewharton.timber)
    testFixturesImplementation(libs.hamcrest)
    testFixturesImplementation(libs.androidx.test.junit)
    // Required so the ExperimentalCoroutinesApi opt-in (applied globally) doesn't cause
    // an "unresolved" warning, which is treated as an error due to allWarningsAsErrors
    testFixturesImplementation(libs.kotlinx.coroutines.core)
}
