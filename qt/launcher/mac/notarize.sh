#!/bin/bash

set -e

# Define output path
OUTPUT_DIR="$1"
APP_LAUNCHER="$OUTPUT_DIR/Anki.app"
ZIP_FILE="$OUTPUT_DIR/Anki.zip"

# Create zip for notarization
(cd "$OUTPUT_DIR" && rm -rf Anki.zip && zip -r Anki.zip Anki.app)

# Upload for notarization
xcrun notarytool submit "$ZIP_FILE" -p default --wait

# Staple the app
xcrun stapler staple "$APP_LAUNCHER"