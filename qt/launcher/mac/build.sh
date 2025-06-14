#!/bin/bash

set -e

# Define output path
OUTPUT_DIR="../../../out/launcher"
APP_LAUNCHER="$OUTPUT_DIR/Anki.app"

# Build rust binary in debug mode
cargo build -p launcher
(cd ../../.. && ./ninja launcher:uv_universal)

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Remove existing app launcher
rm -rf "$APP_LAUNCHER"

# Create app launcher structure
mkdir -p "$APP_LAUNCHER/Contents/MacOS" "$APP_LAUNCHER/Contents/Resources"

# Copy binaries
TARGET_DIR=${CARGO_TARGET_DIR:-target}
cp $TARGET_DIR/debug/launcher "$APP_LAUNCHER/Contents/MacOS/"
cp "$OUTPUT_DIR/uv" "$APP_LAUNCHER/Contents/MacOS/"

# Copy support files
cp Info.plist "$APP_LAUNCHER/Contents/"
cp icon/Assets.car "$APP_LAUNCHER/Contents/Resources/"
cp ../pyproject.toml "$APP_LAUNCHER/Contents/Resources/"

# Codesign
for i in "$APP_LAUNCHER/Contents/MacOS/uv" "$APP_LAUNCHER/Contents/MacOS/launcher" "$APP_LAUNCHER"; do
    codesign --force -vvvv -o runtime -s "Developer ID Application:" \
    --entitlements entitlements.python.xml \
    "$i"
done

# Check
codesign -vvv "$APP_LAUNCHER"
spctl -a "$APP_LAUNCHER"
