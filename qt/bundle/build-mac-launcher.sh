#!/bin/bash

set -e

# Define output path
OUTPUT_DIR="../../out/bundle"
APP_BUNDLE="$OUTPUT_DIR/Anki.app"

# Build rust binary in debug mode
cargo build -p launcher
(cd ../.. && ./ninja bundle:uv_universal)

# Ensure output directory exists
mkdir -p "$OUTPUT_DIR"

# Remove existing app bundle
rm -rf "$APP_BUNDLE"

# Create app bundle structure
mkdir -p "$APP_BUNDLE/Contents/MacOS" "$APP_BUNDLE/Contents/Resources"

# Copy binaries
TARGET_DIR=${CARGO_TARGET_DIR:-target}
cp $TARGET_DIR/debug/launcher "$APP_BUNDLE/Contents/MacOS/"
cp "$OUTPUT_DIR/uv" "$APP_BUNDLE/Contents/MacOS/"

# Copy support files
cp launcher/Info.plist "$APP_BUNDLE/Contents/"
cp launcher/pyproject.toml "$APP_BUNDLE/Contents/Resources/"

# Codesign
for i in "$APP_BUNDLE/Contents/MacOS/uv" "$APP_BUNDLE/Contents/MacOS/launcher" "$APP_BUNDLE"; do
    codesign --force -vvvv -o runtime -s "Developer ID Application:" \
    --entitlements $c/desktop/anki/qt/bundle/mac/entitlements.python.xml \
    "$i"
done

# Check
codesign -vvv "$APP_BUNDLE"
spctl -a "$APP_BUNDLE"

# Mark as quarantined
#xattr -w com.apple.quarantine "0181;$(date +%s);Safari;" "$APP_BUNDLE"

