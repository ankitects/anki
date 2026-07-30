#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../../.." && pwd)"
BRIDGE_DIR="$REPO_ROOT/mobile/ios/rust_bridge"
BUILD_ROOT="${ANKI_IOS_BUILD_ROOT:-$REPO_ROOT/out/ios}"
OUTPUT="${ANKI_IOS_XCFRAMEWORK:-$BUILD_ROOT/AnkiBackend.xcframework}"
BUILD_DEVICE=1

if [[ "${1:-}" == "--simulator-only" ]]; then
  BUILD_DEVICE=0
elif [[ $# -ne 0 ]]; then
  echo "usage: $0 [--simulator-only]" >&2
  exit 2
fi

mkdir -p "$BUILD_ROOT"
rm -rf "$OUTPUT" "$OUTPUT.sha256"

export IPHONEOS_DEPLOYMENT_TARGET="${IPHONEOS_DEPLOYMENT_TARGET:-17.0}"

cd "$REPO_ROOT"
cargo build --locked --release -p anki_ios_bridge --target aarch64-apple-ios-sim

XCFRAMEWORK_ARGS=(
  -library "$REPO_ROOT/target/aarch64-apple-ios-sim/release/libanki_ios_bridge.a"
  -headers "$BRIDGE_DIR/include"
)

if [[ "$BUILD_DEVICE" -eq 1 ]]; then
  cargo build --locked --release -p anki_ios_bridge --target aarch64-apple-ios
  XCFRAMEWORK_ARGS+=(
    -library "$REPO_ROOT/target/aarch64-apple-ios/release/libanki_ios_bridge.a"
    -headers "$BRIDGE_DIR/include"
  )
fi

xcodebuild -create-xcframework "${XCFRAMEWORK_ARGS[@]}" -output "$OUTPUT"

(
  cd "$OUTPUT"
  find . -type f -print0 | LC_ALL=C sort -z | xargs -0 shasum -a 256
) | shasum -a 256 | awk '{print $1}' > "$OUTPUT.sha256"

echo "Created $OUTPUT"
echo "SHA-256 $(cat "$OUTPUT.sha256")"
