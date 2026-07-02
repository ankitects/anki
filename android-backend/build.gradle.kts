import org.gradle.internal.jvm.Jvm
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import kotlin.system.exitProcess

plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.gradle.maven.publish.plugin) apply false
    alias(libs.plugins.ktlint.gradle.plugin) apply false
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
buildscript {
    dependencies {
//        classpath(libs.gradle)
        classpath(libs.kotlin.gradle.plugin)
//        classpath(libs.kotlin.android.extensions)
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
}

// can't be obtained inside 'subprojects'
val ktlintVersion: String = libs.versions.ktlint.get()

// Here we extract per-module "best practices" settings to a single top-level evaluation
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    configure<KtlintExtension> {
        version.set(ktlintVersion)
    }
}

tasks.register<Delete>("clean") {
     delete(rootProject.layout.buildDirectory)
}

ext {
    val jvmVersion = Jvm.current().javaVersion?.majorVersion
    val minSdk = libs.versions.compileSdk.get()
    if (jvmVersion != "17" && jvmVersion != "21" && jvmVersion != "25") {
        println("\n\n\n")
        println("**************************************************************************************************************")
        println("\n\n\n")
        println("ERROR: Anki-Android-Backend builds with JVM version 17, 21 and 25.")
        println("  Incompatible major version detected: '$jvmVersion'")
        if (jvmVersion.parseIntOrDefault(defaultValue = 0) > 25) {
            println("\n\n\n")
            println("  If you receive this error because you want to use a newer JDK, we may accept PRs to support new versions.")
            println("  Edit the main build file, find this message in the file, and add support for the new version.")
            println("  Please make sure the `build.sh` and `check-droid.sh` targets work on an emulator with our minSdk (currently $minSdk).")
        }
        println("\n\n\n")
        println("**************************************************************************************************************")
        println("\n\n\n")
        exitProcess(1)
    }
}

private fun String?.parseIntOrDefault(defaultValue: Int): Int = this?.toIntOrNull() ?: defaultValue
