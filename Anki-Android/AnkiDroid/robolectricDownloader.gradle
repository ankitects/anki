/**
 * Downloads all android-all-instrumented dependencies and copies them to the mavenLocal() repository
 *
 * Once applied to your gradle project, can be executed with ./gradlew robolectricSdkDownload
 */

// The general idea of this was borrowed from https://gist.github.com/xian/05c4f27da6d4156b9827842217c2cd5c
// I then modified it heavily to allow easier addition of new SDK versions
// The full implementation is from https://gist.github.com/simtel12/13ff3e57c37e78e468502b51ebb0f4f2

// List from: https://github.com/robolectric/robolectric/blob/master/robolectric/src/main/java/org/robolectric/plugins/DefaultSdkProvider.java
// This list will need to be updated for new Android SDK versions that come out.
// Note: the PREINSTRUMENTED_VERSION constant is what you put on the end of the artifact

// Only the versions currently used in AnkiDroid Robolectric tests are active, the rest are commented out
// To update these versions, open a terminal in the Anki-Android directory and perform the following steps:
//   1. Run `rm -r ~/.m2` to delete the .m2 directory in your home directory.
//   2. Run `./gradlew jacocoUnitTestReport` to run all unit tests.
//   3. Run `find ~/.m2 -type d -name '*-robolectric-*'`.
//   4. Update the lines below to match the output from `find`.
def robolectricAndroidSdkVersions = [
//        [androidVersion: "6.0.1_r3", frameworkSdkBuildVersion: "r1"],
//        [androidVersion: "7.0.0_r1", frameworkSdkBuildVersion: "r1"],
//        [androidVersion: "7.1.0_r7", frameworkSdkBuildVersion: "r1"],
//        [androidVersion: "8.0.0_r4", frameworkSdkBuildVersion: "r1"],
        [androidVersion: "8.1.0", frameworkSdkBuildVersion: "4611349"],
        [androidVersion: "9", frameworkSdkBuildVersion: "4913185-2"],
        [androidVersion: "10", frameworkSdkBuildVersion:  "5803371"],
        [androidVersion: "11", frameworkSdkBuildVersion:  "6757853"],
        [androidVersion: "12", frameworkSdkBuildVersion:  "7732740"],
        [androidVersion: "12.1", frameworkSdkBuildVersion:  "8229987"],
        [androidVersion: "13", frameworkSdkBuildVersion:  "9030017"],
        [androidVersion: "14", frameworkSdkBuildVersion: "10818077"],
        [androidVersion: "15", frameworkSdkBuildVersion: "13954326"], // current targetSdk
        [androidVersion: "16", frameworkSdkBuildVersion: "13921718"], // used for `@Config(sdk = Build.VERSION_CODES.BAKLAVA)`
]

// Base, public task - will be displayed in ./gradlew robolectricDownloader:tasks
tasks.register('robolectricSdkDownload') {
    group = "Dependencies"
    description = "Downloads all robolectric SDK dependencies into mavenLocal, for use with offline robolectric"
}

// Generate the configuration and actual copy tasks.
robolectricAndroidSdkVersions.forEach { robolectricSdkVersion ->
    // the final part of this `-i<number>` comes from PREINSTRUMENTED_VERSION in upstream DefaultSdkProvider
    def version = "${robolectricSdkVersion['androidVersion']}-robolectric-${robolectricSdkVersion['frameworkSdkBuildVersion']}-i7"

    // Creating a configuration with a dependency allows Gradle to manage the actual resolution of
    // the jar file
    def sdkConfig = configurations.create(version)
    dependencies.add(version, "org.robolectric:android-all-instrumented:${version}")

    // An ArtifactView on the existing configuration gives us the POM alongside the JAR so
    // maven local has both.
    def sdkPomFiles = sdkConfig.incoming.artifactView {
        attributes {
            attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, "pom")
        }
        // if the POM can't be resolved, return an empty collection rather than failing the build
        lenient = true
    }.files

    def mavenLocalFile = new File(this.repositories.mavenLocal().url)
    def mavenRobolectric = new File(mavenLocalFile, "org/robolectric/android-all-instrumented/${version}")
    // Copying all files downloaded for the created configuration into maven local.
    tasks.register("robolectricSdkDownload-${version}", Copy) {
        from sdkConfig
        from sdkPomFiles
        into mavenRobolectric
    }
    robolectricSdkDownload.configure { dependsOn "robolectricSdkDownload-${version}" }
}

