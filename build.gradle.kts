
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.extension.impl.AndroidComponentsExtensionImpl
import com.ichi2.anki.gradle.GitHubActionsTestListener
import com.ichi2.anki.gradle.TestSummaryService
import com.slack.keeper.optInToKeeper
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.internal.jvm.Jvm
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import java.lang.management.ManagementFactory
import java.util.Properties
import kotlin.math.max
import kotlin.system.exitProcess


// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    // Use `id` to avoid classpath conflicts. Versions are pinned by buildSrc/.
    id("com.android.application") apply false
    id("com.android.library") apply false
    id("org.jetbrains.kotlin.android") apply false
    id("org.jetbrains.kotlin.plugin.parcelize") apply false
    id("org.jetbrains.kotlin.jvm") apply false
    // Serialization is a separate artifact, not pinned transitively by AGP.
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint.gradle.plugin) apply false
    alias(libs.plugins.keeper) apply false
}

val localProperties = Properties()
if (project.rootProject.file("local.properties").exists()) {
    localProperties.load(project.rootProject.file("local.properties").inputStream())
}
val fatalWarnings = localProperties["fatal_warnings"] != "false"

// can't be obtained inside 'subprojects'
val ktlintVersion: String = libs.versions.ktlint.get()

// Shared state for the GitHub Actions test summary listener.
// Only registered when running under GitHub Actions; null otherwise.
val testSummaryService = System.getenv("GITHUB_STEP_SUMMARY")?.let { path ->
    gradle.sharedServices.registerIfAbsent("testSummaryService", TestSummaryService::class) {
        parameters.path.set(path)
    }
}

/**
 * Per-fork JVM heap for unit tests.
 * See [Test.setMaxHeapSize]
 */
val unitTestForkMaxHeapGb = 2

// Here we extract per-module "best practices" settings to a single top-level evaluation
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<KtlintExtension> {
        version.set(ktlintVersion)
    }

    afterEvaluate {
        plugins.withType<com.android.build.gradle.BasePlugin> {
            // com.android.lint [BasePlugin] has no `android` extension
            // as it can be applied to java-library
            val androidExtension = extensions.findByName("android") as? CommonExtension ?: return@withType
            androidExtension.testOptions.unitTests {
                isIncludeAndroidResources = true
            }
            androidExtension.testOptions.unitTests.all {
                // tell backend to avoid rollover time, and disable interval fuzzing
                it.environment("ANKI_TEST_MODE", "1")

                it.maxHeapSize = "${unitTestForkMaxHeapGb}g"
                it.minHeapSize = "1g"

                it.useJUnitPlatform()
                it.testLogging {
                    events("failed", "skipped")
                    showStackTraces = true
                    exceptionFormat = TestExceptionFormat.FULL
                }

                // CI: Log test results to the GitHub Actions step summary.
                testSummaryService?.let { service ->
                    it.addTestListener(GitHubActionsTestListener(service))
                }

                it.maxParallelForks = gradleTestMaxParallelForks
                it.forkEvery = 40
                it.systemProperties["junit.jupiter.execution.parallel.enabled"] = true
                it.systemProperties["junit.jupiter.execution.parallel.mode.default"] = "concurrent"
            }

            val androidComponentsExtension =
                extensions.findByName("androidComponents") as AndroidComponentsExtensionImpl<*, *, *>
            androidComponentsExtension.beforeVariants { builder ->
                if (testReleaseBuild && builder.name == "playRelease")
                {
                    builder.optInToKeeper()
                }
            }
        }

        /*
        Related to ExperimentalCoroutinesApi: this opt-in is added to enable usage of experimental
        coroutines API, this targets all project modules except the "api" module,
        which doesn't use coroutines so the annotation isn't available. This would normally
        result in a warning, but we treat warnings as errors.
        (see https://youtrack.jetbrains.com/issue/KT-28777/Using-experimental-coroutines-api-causes-unresolved-dependency)
         */
        tasks.withType(KotlinCompile::class.java).configureEach {
            // We use `isInIdeaSync` to resolve a mismatch between Android Studio's code analyzer
            // and the Kotlin 2.3 compiler regarding Explicit Backing Fields. The IDE requires
            // the unsafe '-XXLanguage' flag to clear false syntax errors, but the actual compiler
            // crashes if it receives this flag due to our strict 'warnings as errors'.
            // This workaround safely feeds the unsafe flag *only* to the IDE during Gradle sync,
            // while passing the standard, crash-free flag to the compiler during the actual build.
            val isInIdeaSync = System.getProperty("idea.sync.active").toBoolean()

            compilerOptions {
                allWarningsAsErrors = fatalWarnings
                val compilerArgs = mutableListOf(
                    // https://youtrack.jetbrains.com/issue/KT-73255
                    // Apply @StringRes to both constructor params and generated properties
                    "-Xannotation-default-target=param-property",
                    "-Xexplicit-backing-fields"
                )

                if (isInIdeaSync) {
                    compilerArgs += "-XXLanguage:+ExplicitBackingFields"
                }

                if (project.path !in listOf(":anki-common", ":api", ":common", ":common:android")) {
                    compilerArgs += "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi"
                }
                if (project.path != ":api") {
                    compilerArgs += "-Xcontext-parameters"
                }
                freeCompilerArgs = compilerArgs
            }
        }
    }
}

// Opt all modules in to lint (with :AnkiDroid pinned to one flavor: 'Full')
val lintAll = tasks.register("lintAll") {
    group = "verification"
    description = "Runs lint on every module."
    // specify 'Full' explicitly so other flavors don't run
    // 'full' has no Manifest changes, so catches more unused references (#15741)
    dependsOn(":AnkiDroid:lintFullDebug")
}

subprojects {
    if (path == ":AnkiDroid") return@subprojects // pinned above to avoid other flavors
    afterEvaluate {
        // 'lintDebug' applies to all Android modules; 'lint' for all JVM modules
        (tasks.findByName("lintDebug") ?: tasks.findByName("lint"))
            ?.let { lintAll.configure { dependsOn(it) } }
    }
}

val jvmVersion = Jvm.current().javaVersion?.majorVersion.parseIntOrDefault(defaultValue = 0)
val minSdk: String = libs.versions.minSdk.get()
val jvmVersionLowerBound = 21
val jvmVersionUpperBound = 25
if (jvmVersion !in jvmVersionLowerBound..jvmVersionUpperBound) {
    println("\n\n\n")
    println("**************************************************************************************************************")
    println("\n\n\n")
    println("ERROR: AnkiDroid builds with JVM versions between $jvmVersionLowerBound and $jvmVersionUpperBound.")
    println("  Incompatible major version detected: '$jvmVersion'")
    println("\n\n\n")
    if (jvmVersion > jvmVersionUpperBound) {
        println("  If you receive this error because you want to use a newer JDK, we may accept PRs to support new versions.")
        println("  Edit the main build.gradle file, find this message in the file, and add support for the new version.")
        println("  Please make sure the `jacocoTestReport` target works on an emulator with our minSdk (currently $minSdk).")
    } else {
        println("  Please update: Settings - Build, Execution, Deployment - Build Tools - Gradle - Gradle JDK")
    }
    println("\n\n\n")
    println("**************************************************************************************************************")
    println("\n\n\n")
    exitProcess(1)
}

val ciBuild by extra(System.getenv("CI") == "true") // true when running on GitHub Actions
val isMacOs = System.getProperty("os.name") == "Mac OS X"
// allows for -Dpre-dex=false to be set
val preDexEnabled by extra("true" == System.getProperty("pre-dex", "true"))
// allows for universal APKs to be generated
val universalApkEnabled by extra("true" == System.getProperty("universal-apk", "false"))

val testReleaseBuild by extra(System.getenv("TEST_RELEASE_BUILD") == "true")
var androidTestVariantName by extra(
    if (testReleaseBuild) "Release" else "Debug"
)

private fun sysctl(key: String): Long =
    providers.exec {
        commandLine("sysctl", "-n", key)
    }.standardOutput.asText.get().trim().toLong()

/**
 * The Gradle daemon's `-Xmx` max heap, in bytes.
 *
 * Reads from the launch flags as `getRuntime().maxMemory()` is GC-dependent.
 *
 * @throws IllegalStateException if `-Xmx` is missing or invalid.
 */
private fun gradleDaemonHeapBytes(): Long {
    val xmx = ManagementFactory.getRuntimeMXBean().inputArguments
        .lastOrNull { it.startsWith("-Xmx") } // last -Xmx wins, as in the JVM
        ?: error("Gradle daemon has no -Xmx flag")
    val match = Regex("-Xmx(\\d+)([MG])", RegexOption.IGNORE_CASE).matchEntire(xmx)
        ?: error("Cannot parse Gradle daemon heap from '$xmx'; expected -Xmx<n>M or -Xmx<n>G")
    val size = match.groupValues[1].toLong()
    val byteMultiplier = when (match.groupValues[2].uppercase()) {
        "G" -> 1024L * 1024 * 1024
        else -> 1024L * 1024 // M
    }
    return size * byteMultiplier
}

val gradleTestMaxParallelForks by extra(
    if (isMacOs) {
        // macOS reports hardware cores.
        // This is accurate for CI, Intel (halved due to SMT) and Apple Silicon
        val physicalCpus = sysctl("hw.physicalcpu")

        if (ciBuild) {
            // #21168: The `macos-14` CI runner has only 7GB RAM and OOMs (exit 134) so bound by RAM.
            // Reserve the daemon's own heap (the OS shares its slack); split the rest into forks.
            val forkHeapBytes = unitTestForkMaxHeapGb * 1024L * 1024 * 1024
            val availableBytes = sysctl("hw.memsize") - gradleDaemonHeapBytes()
            val memoryBoundForkProcesses = max(1L, availableBytes / forkHeapBytes)
            minOf(physicalCpus, memoryBoundForkProcesses).toInt()
        } else {
            physicalCpus.toInt()
        }
    } else if (ciBuild) {
        // GitHub Actions run on Standard_D4ads_v5 Azure Compute Units with 4 vCPUs
        // They appear to be 2:1 vCPU to CPU on Linux/Windows with two vCPU cores but with performance 1:1-similar
        // Sources to determine the correct Azure Compute Unit (and get CPU count) to tune this:
        // Which Azure compute unit in use? https://github.com/github/docs/blob/a25a33bb6cbf86a629d0a0c7bef624743991f97e/content/actions/using-github-hosted-runners/about-github-hosted-runners/about-github-hosted-runners.md?plain=1#L176
        // What is that compute unit? https://learn.microsoft.com/en-us/azure/virtual-machines/dasv5-dadsv5-series#dadsv5-series
        // How does it perform? https://learn.microsoft.com/en-gb/azure/virtual-machines/linux/compute-benchmark-scores#dadsv5 (vs previous Standard_DS2_v2 https://learn.microsoft.com/en-gb/azure/virtual-machines/linux/compute-benchmark-scores#dv2---general-compute)
        4
    } else {
        // Use 50% of cores to account for SMT which doesn't help this workload
        max(1, Runtime.getRuntime().availableProcessors() / 2)
    }
)

private fun String?.parseIntOrDefault(defaultValue: Int): Int = this?.toIntOrNull() ?: defaultValue