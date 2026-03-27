#!/bin/bash

set -e

ANKI_VERSION=$1
BUILD_DIR=$2
OUTPUT_DIR="./dist"
mkdir -p "$OUTPUT_DIR"
HOST_ARCH=$(uname -m)

mv "$BUILD_DIR/Anki" "$BUILD_DIR/anki"

# Set executable permissions
chmod +x \
    "$BUILD_DIR/anki" \
    "$BUILD_DIR/install.sh" \
    "$BUILD_DIR/uninstall.sh"

# Set proper permissions and create tarball
chmod -R a+r "$BUILD_DIR"

ZSTD="zstd -c --long -T0 -18"
TRANSFORM="s%^.%anki-$ANKI_VERSION-linux%S"
TARBALL="$OUTPUT_DIR/anki-$ANKI_VERSION-linux-$HOST_ARCH.tar.zst"

tar -I "$ZSTD" --transform "$TRANSFORM" -cf "$TARBALL" -C "$BUILD_DIR" .
rm -rf "$BUILD_DIR"

echo $TARBALL
