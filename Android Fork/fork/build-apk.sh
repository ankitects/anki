#!/usr/bin/env bash
# Builds the Anki Time APK: AnkiDroid ("Android Fork") on top of this repository's Rust core
# (rslib, with the RWKV time budgets), via AnkiDroid's backend (Anki-Android-Backend).
#
# Usage: "Android Fork/fork/build-apk.sh" [work dir]   (default work dir: out/android)
#
# Needs: git, rsync, JDK 21, rustup, the Android SDK in ANDROID_HOME (platforms;android-36,
# platforms;android-37.0, build-tools;36.0.0) and the NDK version below.
#
# The core is the committed HEAD of this repository: commit before building.
#
# Optional environment:
#   FORK_VERSION_CODE, FORK_VERSION_NAME   version of the APK (CI sets them; see README.md)
#   KEYSTOREPATH, KEYSTOREPWD, KEYALIAS, KEYPWD
#                                          signing key; without one, AnkiDroid's public
#                                          fallback key (tools/fallback-release-keystore.jks)
#   ANDROID_ABI                            arm64-v8a (default), armeabi-v7a or x86_64

set -euo pipefail

# The Anki-Android-Backend commit to build with (adopts anki 26.09.4, matching the AnkiDroid
# version in "Android Fork"). Update both together; see README.md.
BACKEND_REPO=https://github.com/ankidroid/Anki-Android-Backend
BACKEND_COMMIT=2e96cb58447a2f3d34275fa370f94711340b8bc6
NDK_VERSION=29.0.14206865

FORK_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
APP_SRC=$(dirname "$FORK_DIR")
REPO=$(git -C "$FORK_DIR" rev-parse --show-toplevel)
WORK=${1:-$REPO/out/android}
ABI=${ANDROID_ABI:-arm64-v8a}
case "$ABI" in
    arm64-v8a) ARCH=arm64 ;;
    armeabi-v7a) ARCH=armv7 ;;
    x86_64) ARCH=x86_64 ;;
    *) echo "unsupported ANDROID_ABI: $ABI" >&2; exit 1 ;;
esac

: "${ANDROID_HOME:?set ANDROID_HOME to the Android SDK}"
export ANDROID_NDK_HOME=${ANDROID_NDK_HOME:-$ANDROID_HOME/ndk/$NDK_VERSION}
[ -d "$ANDROID_NDK_HOME" ] || { echo "NDK $NDK_VERSION not found in $ANDROID_NDK_HOME" >&2; exit 1; }

mkdir -p "$WORK"
# AnkiDroid looks for the backend in ../Anki-Android-Backend, so the two sit side by side
BACKEND=$WORK/Anki-Android-Backend
APP=$WORK/Anki-Android

echo "*** Backend ($BACKEND_COMMIT)"
if [ ! -d "$BACKEND/.git" ]; then
    git init -q "$BACKEND"
    git -C "$BACKEND" remote add origin "$BACKEND_REPO"
fi
if [ "$(git -C "$BACKEND" rev-parse -q --verify HEAD || true)" != "$BACKEND_COMMIT" ]; then
    git -C "$BACKEND" fetch -q --depth 1 origin "$BACKEND_COMMIT"
    git -C "$BACKEND" checkout -q --force --detach FETCH_HEAD
fi
# build switches: one architecture, no host library for unit tests
git -C "$BACKEND" checkout -q -- build_rust
git -C "$BACKEND" apply "$FORK_DIR/backend.patch"

echo "*** Core: $(git -C "$REPO" log -1 --format='%h %s')"
if ! git -C "$REPO" diff --quiet HEAD -- rslib proto ts ftl; then
    echo "warning: uncommitted changes to the core are not included in the APK" >&2
fi
CORE=$BACKEND/anki
if [ ! -d "$CORE/.git" ]; then
    rm -rf "$CORE"
    git clone -q --no-checkout "$REPO" "$CORE"
fi
git -C "$CORE" fetch -q "$REPO" HEAD
git -C "$CORE" checkout -q --force --detach FETCH_HEAD
git -C "$CORE" submodule update -q --init --depth 1 ftl/core-repo ftl/qt-repo

echo "*** App: $APP_SRC"
rsync -a --delete \
    --exclude .gradle --exclude build --exclude .kotlin --exclude local.properties \
    "$APP_SRC/" "$APP/"
printf 'sdk.dir=%s\nlocal_backend=true\n' "$ANDROID_HOME" > "$APP/local.properties"

echo "*** Building the backend (Rust core and web pages)"
command -v n2 > /dev/null || bash "$CORE/tools/install-n2"
(
    cd "$BACKEND"
    export RELEASE=1 ANDROID_ARCH=$ARCH SKIP_ROBOLECTRIC=1
    # keep the build machine's paths out of the library
    export ANDROID_RUSTFLAGS="--remap-path-prefix=$WORK=/build --remap-path-prefix=$HOME=/home"
    cargo run -p build_rust
    # its Gradle daemon would hold memory during the app build
    ./gradlew --stop > /dev/null || true
)

echo "*** Building the app"
VERSION_ARGS=()
[ -n "${FORK_VERSION_CODE:-}" ] && VERSION_ARGS+=("-PforkVersionCode=$FORK_VERSION_CODE")
[ -n "${FORK_VERSION_NAME:-}" ] && VERSION_ARGS+=("-PforkVersionName=$FORK_VERSION_NAME")
if [ -z "${KEYSTOREPATH:-}" ]; then
    echo "warning: no signing key given: signing with AnkiDroid's public fallback key" >&2
fi
(
    cd "$APP"
    ./gradlew :AnkiDroid:assembleFullRelease \
        "-PabiFilter=$ABI" \
        -PcustomSuffix=timefork \
        "-PcustomName=Anki Time" \
        -PforkAcraUrl=https://localhost.invalid/acra \
        "-PforkCommitHash=$(git -C "$REPO" rev-parse HEAD)" \
        "${VERSION_ARGS[@]}"
)

mkdir -p "$WORK/apk"
OUT=$WORK/apk/AnkiTime-$ABI.apk
cp "$APP/AnkiDroid/build/outputs/apk/full/release/AnkiDroid-full-$ABI-release.apk" "$OUT"
echo "*** Built $OUT"
