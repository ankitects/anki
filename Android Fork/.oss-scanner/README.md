<!-- SPDX-License-Identifier: GPL-3.0-or-later -->

# OSS Scanner

The [Anthropic OSS Scanner](https://github.com/anthropics/oss-scanner) builds this
Dockerfile with network access, then audits the checkout at `/src` offline.
The image contains a debug APK, compiled app/library tests, smoke-test results,
and the dependencies needed to rebuild and run tests offline.

## Build and verify

Run from the root of a regular clone. Allow 16 GiB RAM for a local image build,
especially under emulation; the scanner provides a larger build machine.

```sh
docker build --platform=linux/amd64 -f .oss-scanner/Dockerfile -t ankidroid-oss-scanner .
docker run --rm --platform=linux/amd64 --network=none \
  --cpus=2 --memory=8g --memory-swap=8g ankidroid-oss-scanner \
  ./gradlew --offline --no-daemon --console=plain --no-build-cache \
  :AnkiDroid:assemblePlayDebug --rerun-tasks
docker run --rm --platform=linux/amd64 --network=none \
  --cpus=2 --memory=8g --memory-swap=8g ankidroid-oss-scanner \
  ./gradlew --offline --no-daemon --console=plain --no-build-cache \
  -Pkotlin.compiler.execution.strategy=in-process \
  :AnkiDroid:testPlayDebugUnitTest --rerun
docker run --rm --platform=linux/amd64 --network=none \
  --cpus=2 --memory=8g --memory-swap=8g ankidroid-oss-scanner \
  ./gradlew --offline --no-daemon --console=plain -p buildSrc test --rerun
```

The image build uses `--test-dry-run` to compile app/library tests and cache their
runtime dependencies, then runs a small offline smoke suite. The full test and
coverage run was trimmed to fit the scanner's 45-minute image-build budget.
Dry-run reports contain skipped tests. The build-logic tests still run to populate
their nested Gradle TestKit caches.

The separate offline checks above rebuild the APK and rerun tests in containers
limited to 2 CPUs and 8 GiB RAM, with swap disabled. The test commands allow Gradle
to reuse compiled prerequisites. The app test command uses
[in-process Kotlin compilation](https://kotlinlang.org/docs/compiler-execution-strategy.html)
so compiler daemons do not remain resident alongside the test JVM. Prerequisite
compilation can still run even when only the test task is forced to rerun.
Avoid forcing all compilation and test tasks to
rerun together on an 8 GiB audit machine: compiler and test JVMs can exceed that
limit, particularly under emulation. Use the task-specific `--rerun` when rerunning
tests, and run a complete recompilation separately.

The `--network=none` checks isolate external networking. Anki pages and card viewers
use WebView-served assets and `PostRequestBridge` instead of a localhost HTTP
server, so local asset loading and bridge calls do not need network sockets.

Use Linux amd64 even on an ARM host: Android's Linux build tools and the scanner
run on x86-64. A full build under emulation can take substantially longer.
The Docker build context must include a self-contained `.git` directory. For a
linked worktree, make a standalone local clone first and copy any uncommitted
changes into it; the worktree's `.git` file points outside the build context.
Local Gradle caches, build outputs and `local.properties` are excluded from the build context.

The build uses the project's pinned Gradle and JetBrains JDK, and reads the Android
platform version from `gradle/libs.versions.toml`. Update the command-line tools
version and checksum together using Google's [download page](https://developer.android.com/studio#command-tools).
Do not remove Gradle caches from the finished image: the scanner needs them offline.
This also includes `buildSrc/build/test-kit`, which holds dependencies for the
build logic's nested test builds; use `test --rerun` instead of cleaning `buildSrc`.

## Enrollment

> [!NOTE]
> This integration is experimental. If AnkiDroid is not accepted into OSS Scanner
> within three months, it is a candidate for reversion.

After these files are available on AnkiDroid's default branch, a maintainer can copy
[project.yaml](project.yaml) to `projects/ankidroid/project.yaml` in
`anthropics/oss-scanner`, then run that repository's `tools/validate.py` and
`tools/check ankidroid`. Enrollment is a separate maintainer action; adding these
files does not enable scans.
