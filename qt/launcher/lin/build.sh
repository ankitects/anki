#!/bin/bash

set -e

# Add Linux cross-compilation target
rustup target add aarch64-unknown-linux-gnu

# Define output paths
OUTPUT_DIR="../../../out/launcher"
LAUNCHER_DIR="$OUTPUT_DIR/anki-launcher"

# Clean existing output directory
rm -rf "$LAUNCHER_DIR"

# Build binaries for both Linux architectures
cargo build -p launcher --release --target x86_64-unknown-linux-gnu
CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc \
    cargo build -p launcher --release --target aarch64-unknown-linux-gnu
(cd ../../.. && ./ninja extract:uv_lin_arm)

# Create output directory
mkdir -p "$LAUNCHER_DIR"

# Copy binaries and support files
TARGET_DIR=${CARGO_TARGET_DIR:-../../../target}

# Copy launcher binaries with architecture suffixes
cp "$TARGET_DIR/x86_64-unknown-linux-gnu/release/launcher" "$LAUNCHER_DIR/launcher.amd64"
cp "$TARGET_DIR/aarch64-unknown-linux-gnu/release/launcher" "$LAUNCHER_DIR/launcher.arm64"

# Copy uv binaries with architecture suffixes
cp "../../../out/extracted/uv/uv" "$LAUNCHER_DIR/uv.amd64"
cp "../../../out/extracted/uv_lin_arm/uv" "$LAUNCHER_DIR/uv.arm64"

# Copy support files from lin directory
for file in README.md anki.1 anki.desktop anki.png anki.xml anki.xpm install.sh uninstall.sh anki; do
    cp "$file" "$LAUNCHER_DIR/"
done

# Copy additional files from parent directory
cp ../pyproject.toml "$LAUNCHER_DIR/"
cp ../../../.python-version "$LAUNCHER_DIR/"

# Set executable permissions
chmod +x \
    "$LAUNCHER_DIR/anki" \
    "$LAUNCHER_DIR/launcher.amd64" \
    "$LAUNCHER_DIR/launcher.arm64" \
    "$LAUNCHER_DIR/uv.amd64" \
    "$LAUNCHER_DIR/uv.arm64" \
    "$LAUNCHER_DIR/install.sh" \
    "$LAUNCHER_DIR/uninstall.sh"

# Set proper permissions and create tarball
chmod -R a+r "$LAUNCHER_DIR"

# Create tarball using the same options as the Rust template
ZSTD="zstd -c --long -T0 -18"
TRANSFORM="s%^.%anki-launcher%S"
TARBALL="$OUTPUT_DIR/anki-launcher.tar.zst"

tar -I "$ZSTD" --transform "$TRANSFORM" -cf "$TARBALL" -C "$LAUNCHER_DIR" .

echo "Build complete:"
echo "Universal launcher: $LAUNCHER_DIR"
echo "Tarball: $TARBALL"
