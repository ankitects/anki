#!/usr/bin/env bash
# Build (and optionally run/test) the AnkiMCAT iOS app on the arm64 iOS
# simulator. Compiles + links the SwiftUI app against the anki-ios C ABI
# staticlib (out/ios/Anki.xcframework) and swift-protobuf.
#
# Usage:
#   ./build-sim.sh                # generate project + build for the sim SDK
#   ./build-sim.sh run  [UDID]    # build, install, launch on a booted device
#   ./build-sim.sh test [UDID]    # build + run the live C3/C4 XCUITest
#
# Requires: xcodegen, Xcode iOS simulator SDK. Running/testing additionally
# requires an installed iOS simulator runtime + a booted device (UDID). If no
# UDID is given, the first booted device is used.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

MODE="${1:-build}"
UDID="${2:-}"

# Regenerate the xcodeproj from project.yml (idempotent).
xcodegen generate

# Resolve a usable simulator UDID and guarantee it is booted & ready.
# Order of preference: explicit arg -> already-booted device -> first
# available iPhone -> freshly created iPhone. Then boot it (idempotent),
# open the Simulator UI, and wait until it is fully ready to install/launch.
resolve_udid() {
  # NB: these run under `set -euo pipefail`; a grep with no match exits 1 and
  # would abort the whole script, so each substitution ends with `|| true`.

  # 1. Prefer an already-booted device when none was given.
  if [[ -z "$UDID" ]]; then
    UDID=$( { xcrun simctl list devices booted 2>/dev/null \
      | grep -oE '[0-9A-F-]{36}' | head -1; } || true )
  fi

  # 2. Otherwise reuse any existing iPhone device.
  if [[ -z "$UDID" ]]; then
    UDID=$( { xcrun simctl list devices available 2>/dev/null \
      | grep -iE 'iPhone' | grep -oE '[0-9A-F-]{36}' | head -1; } || true )
  fi

  # 3. As a last resort, create one on the newest installed iOS runtime.
  if [[ -z "$UDID" ]]; then
    echo "No simulator found; creating one..."
    RT=$( { xcrun simctl list runtimes 2>/dev/null \
      | grep -oE 'com.apple.CoreSimulator.SimRuntime.iOS[0-9-]+' | tail -1; } || true )
    DT=$( { xcrun simctl list devicetypes 2>/dev/null \
      | grep -oE 'com.apple.CoreSimulator.SimDeviceType.iPhone-[0-9A-Za-z-]+' | tail -1; } || true )
    if [[ -z "$RT" || -z "$DT" ]]; then
      echo "No iOS simulator runtime installed. Install one via Xcode > Settings > Components." >&2
      exit 1
    fi
    UDID=$(xcrun simctl create "anki-mcat" "$DT" "$RT")
  fi

  echo "Using simulator: $UDID"
  # Boot (idempotent) and wait until fully ready; open the Simulator window.
  xcrun simctl bootstatus "$UDID" -b >/dev/null 2>&1 || true
  open -a Simulator >/dev/null 2>&1 || true
}

case "$MODE" in
  build)
    # Build for the simulator SDK. A booted device (destination) is used when
    # available; otherwise falls back to the generic simulator SDK build.
    if UDID_TMP=$(xcrun simctl list devices booted 2>/dev/null | grep -oE '[0-9A-F-]{36}' | head -1) && [[ -n "$UDID_TMP" ]]; then
      xcodebuild build -project AnkiMCAT.xcodeproj -scheme AnkiMCAT \
        -destination "id=$UDID_TMP" -configuration Debug CODE_SIGNING_ALLOWED=NO
    else
      echo "No booted device; building against the generic iOS Simulator SDK."
      xcodebuild build -project AnkiMCAT.xcodeproj -scheme AnkiMCAT \
        -sdk iphonesimulator -configuration Debug ARCHS=arm64 \
        CODE_SIGNING_ALLOWED=NO
    fi
    ;;

  run)
    resolve_udid
    xcodebuild build -project AnkiMCAT.xcodeproj -scheme AnkiMCAT \
      -destination "id=$UDID" -configuration Debug CODE_SIGNING_ALLOWED=NO
    APP=$(xcodebuild -project AnkiMCAT.xcodeproj -scheme AnkiMCAT -showBuildSettings \
      -destination "id=$UDID" 2>/dev/null | awk '/ BUILT_PRODUCTS_DIR / {print $3}' | head -1)/AnkiMCAT.app
    xcrun simctl install "$UDID" "$APP"
    xcrun simctl launch "$UDID" net.ankiweb.mcat.AnkiMCAT
    echo "✅ AnkiMCAT launched on simulator $UDID"
    echo "   (stream logs with: xcrun simctl launch --console-pty $UDID net.ankiweb.mcat.AnkiMCAT)"
    ;;

  test)
    resolve_udid
    xcodebuild test -project AnkiMCAT.xcodeproj -scheme AnkiMCAT \
      -destination "id=$UDID" -configuration Debug \
      CODE_SIGNING_ALLOWED=NO CODE_SIGNING_REQUIRED=NO
    ;;

  *)
    echo "Unknown mode: $MODE (expected build|run|test)" >&2
    exit 1
    ;;
esac
