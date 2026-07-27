#!/bin/bash

set -e

if [ $(uname -s) != "Darwin" ]; then
    echo "This script can only be run on macOS"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
OUTPUT_DIR="$SCRIPT_DIR/../../out/extracted"
BREW_PREFIX="$(brew --prefix)"
BREW_REPO="$(brew --repository)"

brew install dylibbundler
brew install lame
if [ "$(brew list --full-name | grep ankitects/audio/mpv)" = "" ]; then
    brew uninstall mpv || true
    brew tap-new ankitects/audio || true
    cp "$SCRIPT_DIR/mpv.rb" "$BREW_REPO/Library/Taps/ankitects/homebrew-audio/Formula/"
    brew install ankitects/audio/mpv
fi

rm -rf "$OUTPUT_DIR/mpv"
mkdir -p "$OUTPUT_DIR/mpv"
pushd "$OUTPUT_DIR/mpv"
mkdir libs
cp "$BREW_PREFIX/bin/mpv" .
dylibbundler -x mpv -d libs -p @executable_path/libs/ -b
popd

rm -rf "$OUTPUT_DIR/lame"
mkdir -p "$OUTPUT_DIR/lame"
cp "$BREW_PREFIX/bin/lame" "$OUTPUT_DIR/lame/" && chmod u+w "$OUTPUT_DIR/lame/lame"

if [ -n "${SIGN_IDENTITY:-}" ]; then
    find "$OUTPUT_DIR/mpv/libs" -name "*.dylib" -exec \
        codesign --sign "$SIGN_IDENTITY" --force --options runtime --timestamp {} \;
    codesign --sign "$SIGN_IDENTITY" --force --options runtime --timestamp "$OUTPUT_DIR/mpv/mpv"
    codesign --sign "$SIGN_IDENTITY" --force --options runtime --timestamp "$OUTPUT_DIR/lame/lame"
fi

./ninja audio_wheel
