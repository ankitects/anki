# SPDX-License-Identifier: GPL-3.0-or-later
# Build from the repository root with --platform=linux/amd64 (Android SDK tools).
FROM ubuntu:24.04

RUN test "$(dpkg --print-architecture)" = amd64 \
    && apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
        ca-certificates curl git openjdk-21-jdk-headless python3 unzip \
        fontconfig libasound2t64 libgl1 libxi6 libxrender1 libxtst6 \
    && rm -rf /var/lib/apt/lists/*

ENV ANDROID_HOME=/opt/android-sdk \
    GRADLE_USER_HOME=/opt/gradle \
    JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 \
    LANG=C.UTF-8 \
    JAVA_TOOL_OPTIONS=-XX:ActiveProcessorCount=2
ENV PATH="${ANDROID_HOME}/cmdline-tools/latest/bin:${ANDROID_HOME}/platform-tools:${JAVA_HOME}/bin:${PATH}"

# https://developer.android.com/studio#command-tools
ARG COMMAND_LINE_TOOLS_VERSION=15859902
ARG COMMAND_LINE_TOOLS_SHA256=4e4c464f145a7512b57d088ac6c278c03c9eea610886b35a5e0804e74eedf583
RUN curl --fail --location --retry 3 \
        "https://dl.google.com/android/repository/commandlinetools-linux-${COMMAND_LINE_TOOLS_VERSION}_latest.zip" \
        --output /tmp/commandlinetools.zip \
    && echo "${COMMAND_LINE_TOOLS_SHA256}  /tmp/commandlinetools.zip" | sha256sum --check - \
    && mkdir -p "${ANDROID_HOME}/cmdline-tools" \
    && unzip -q /tmp/commandlinetools.zip -d "${ANDROID_HOME}/cmdline-tools" \
    && mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest" \
    && rm /tmp/commandlinetools.zip \
    && yes | sdkmanager --licenses > /dev/null

# Keep the caches in the image: the audit has no network or BuildKit cache mounts.
# Limit concurrent compilation and test JVMs for the scanner's 8 GiB audit machine.
RUN mkdir -p "${GRADLE_USER_HOME}" \
    && printf 'org.gradle.workers.max=2\norg.gradle.parallel=false\n' > "${GRADLE_USER_HOME}/gradle.properties"

# The scanner supplies a regular git clone, including .git for BuildConfig.
COPY . /src
WORKDIR /src

# Read the platform from the version catalog. Gradle installs its required build-tools
# version during the online build, using the licenses accepted above.
RUN sdkmanager "platform-tools" \
        "platforms;android-$(python3 -c 'import tomllib; v = tomllib.load(open("gradle/libs.versions.toml", "rb"))["versions"]; print(v["compileSdk"] + "." + v["compileSdkMinor"])')"

# The full unit-test/coverage run was trimmed to smoke tests to stay within the
# scanner's 45-minute image-build budget. Dry runs compile the app/library tests
# and cache their runtime dependencies without executing the full suite.
RUN ./gradlew --no-daemon --console=plain --stacktrace \
        :AnkiDroid:assemblePlayDebug :AnkiDroid:robolectricSdkDownload \
        :AnkiDroid:testPlayDebugUnitTest --test-dry-run \
        :anki-common:testDebugUnitTest --test-dry-run \
        :api:testDebugUnitTest --test-dry-run \
        :common:test --test-dry-run \
        :common:android:testDebugUnitTest --test-dry-run \
        :compat:testDebugUnitTest --test-dry-run \
        :libanki:test --test-dry-run \
        :note-editor:testDebugUnitTest --test-dry-run \
        :widgets:testDebugUnitTest --test-dry-run

# Exercise the native backend, Robolectric and multimedia import offline.
# Force real execution after the dry run; a failed smoke test fails the image.
RUN --network=none ./gradlew --offline --no-daemon --console=plain --stacktrace \
        :AnkiDroid:testPlayDebugUnitTest --no-test-dry-run --rerun \
        --tests com.ichi2.anki.CardUtilsTest \
        --tests com.ichi2.anki.InfoTest \
        --tests com.ichi2.anki.MediaRegistrationTest

# Run after the app build has installed AGP's build-tools. Installing them during
# the build logic tests would invalidate the configuration cache they check.
RUN ./gradlew --no-daemon --console=plain --stacktrace -p buildSrc test

CMD ["bash"]
