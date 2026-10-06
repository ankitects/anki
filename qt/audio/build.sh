#!/usr/bin/env bash
#
# Builds the macOS anki-audio wheel. mpv, lame and all their libraries are
# built from source (see macos/)

set -e

if [ $(uname -s) != "Darwin" ]; then
    echo "This script can only be run on macOS"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../../out/extracted"

brew install meson ninja cmake pkgconf nasm autoconf automake libtool dylibbundler

# FFmpeg's Metal filters need the Metal compiler, which newer Xcode versions
# ship as a separate download.
if ! xcrun -sdk macosx metal -v >/dev/null 2>&1; then
    xcodebuild -downloadComponent MetalToolchain
fi

"$SCRIPT_DIR/macos/build-deps.sh"
"$SCRIPT_DIR/macos/bundle.sh"
"$SCRIPT_DIR/macos/check-binaries.sh" "$OUTPUT_DIR/mpv" "$OUTPUT_DIR/lame"

if [ -n "${SIGN_IDENTITY:-}" ]; then
    find "$OUTPUT_DIR/mpv/libs" -name "*.dylib" -exec \
        codesign --sign "$SIGN_IDENTITY" --force --options runtime --timestamp {} \;
    codesign --sign "$SIGN_IDENTITY" --force --options runtime --timestamp "$OUTPUT_DIR/mpv/mpv"
    codesign --sign "$SIGN_IDENTITY" --force --options runtime --timestamp "$OUTPUT_DIR/lame/lame"
fi

./ninja audio_wheel
