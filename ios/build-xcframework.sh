#!/usr/bin/env bash
# Build Anki.xcframework from the anki-ios staticlib for the iOS simulator.
#
# C2 scope: only the aarch64-apple-ios-sim slice is packaged (that is the
# checkpoint target). A device/fat build (aarch64-apple-ios + a real
# multi-arch xcframework) is future work for shipping, not for C2.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CRATE_TARGET="aarch64-apple-ios-sim"
LIB="${REPO_ROOT}/target/${CRATE_TARGET}/debug/libanki_ios.a"
HEADERS="${REPO_ROOT}/ios/include"
OUT="${REPO_ROOT}/out/ios/Anki.xcframework"

echo ">> building staticlib for ${CRATE_TARGET}"
cargo build -p anki-ios --target "${CRATE_TARGET}" --manifest-path "${REPO_ROOT}/Cargo.toml"

echo ">> (re)creating xcframework at ${OUT}"
rm -rf "${OUT}"
xcodebuild -create-xcframework \
    -library "${LIB}" \
    -headers "${HEADERS}" \
    -output "${OUT}"

echo ">> done: ${OUT}"
