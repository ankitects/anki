#!/usr/bin/env bash
# C2: build the RoundTrip Swift executable for arm64 iphonesimulator, then run
# it on a booted simulator via `xcrun simctl spawn`. Prints the program's
# stdout (look for the "C2-ROUNDTRIP: PASS" line).
#
# Prereqs: an iOS simulator runtime installed (xcodebuild -downloadPlatform iOS)
# and a booted device named "anki-c2" (see below; the script boots it if a UDID
# is passed or discoverable).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PKG_DIR="${REPO_ROOT}/ios/RoundTrip"
SDK="$(xcrun --sdk iphonesimulator --show-sdk-path)"
LIBDIR="${REPO_ROOT}/target/aarch64-apple-ios-sim/debug"
TRIPLE="arm64-apple-ios18.0-simulator"

echo ">> building staticlib (ensures libanki_ios.a is current)"
cargo build -p anki-ios --target aarch64-apple-ios-sim --manifest-path "${REPO_ROOT}/Cargo.toml"

echo ">> building RoundTrip for ${TRIPLE}"
cd "${PKG_DIR}"
swift build \
    --triple "${TRIPLE}" \
    --sdk "${SDK}" \
    -Xcc -isysroot -Xcc "${SDK}" \
    -Xswiftc -sdk -Xswiftc "${SDK}" \
    -Xlinker -L -Xlinker "${LIBDIR}" \
    -Xlinker -lanki_ios

BIN="$(swift build --triple "${TRIPLE}" --sdk "${SDK}" --show-bin-path)/RoundTrip"
echo ">> built: ${BIN}"

# Resolve the booted device (prefer one named anki-c2).
UDID="${1:-$(xcrun simctl list devices booted | grep -oE '[0-9A-F-]{36}' | head -1)}"
if [ -z "${UDID}" ]; then
    echo "!! no booted simulator; create+boot one, e.g.:"
    echo "   xcrun simctl create anki-c2 com.apple.CoreSimulator.SimDeviceType.iPhone-16 <runtime-id>"
    echo "   xcrun simctl boot <udid>"
    exit 2
fi
echo ">> spawning on booted device ${UDID}"
# IPHONE_SIMULATOR_ROOT lets the Rust CoreFoundation shim locate the sim root.
xcrun simctl spawn "${UDID}" "${BIN}"
