#!/bin/bash

set -e

# Define output path
OUTPUT_DIR="../../../out/launcher"
APP_LAUNCHER="$OUTPUT_DIR/Anki.app"
rm -rf "$APP_LAUNCHER"

# Build binaries for both architectures
rustup target add aarch64-apple-darwin x86_64-apple-darwin
cargo build -p launcher --release --target aarch64-apple-darwin
cargo build -p launcher --release --target x86_64-apple-darwin
(cd ../../.. && ./ninja launcher:uv_universal)

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Remove existing app launcher
rm -rf "$APP_LAUNCHER"

# Create app launcher structure
mkdir -p "$APP_LAUNCHER/Contents/MacOS" "$APP_LAUNCHER/Contents/Resources"

# Copy binaries in
TARGET_DIR=${CARGO_TARGET_DIR:-target}
lipo -create \
    "$TARGET_DIR/aarch64-apple-darwin/release/launcher" \
    "$TARGET_DIR/x86_64-apple-darwin/release/launcher" \
    -output "$APP_LAUNCHER/Contents/MacOS/launcher"
cp "$OUTPUT_DIR/uv" "$APP_LAUNCHER/Contents/MacOS/"

# Copy support files
cp Info.plist "$APP_LAUNCHER/Contents/"
cp icon/Assets.car "$APP_LAUNCHER/Contents/Resources/"
cp ../pyproject.toml "$APP_LAUNCHER/Contents/Resources/"
cp ../../../.python-version "$APP_LAUNCHER/Contents/Resources/"

# Codesign
for i in "$APP_LAUNCHER/Contents/MacOS/uv" "$APP_LAUNCHER/Contents/MacOS/launcher" "$APP_LAUNCHER"; do
    codesign --force -vvvv -o runtime -s "Developer ID Application:" \
    --entitlements entitlements.python.xml \
    "$i"
done

# Check
codesign -vvv "$APP_LAUNCHER"
spctl -a "$APP_LAUNCHER"

# Notarize
./notarize.sh "$OUTPUT_DIR"

# Bundle
./dmg/build.sh "$OUTPUT_DIR"