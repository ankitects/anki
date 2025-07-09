#!/bin/bash
#
# This script currently only supports universal builds on x86_64.
#

set -e

# Add Linux cross-compilation target
rustup target add aarch64-unknown-linux-gnu
# Detect host architecture
HOST_ARCH=$(uname -m)


# Define output paths
OUTPUT_DIR="../../../out/launcher"
LAUNCHER_DIR="$OUTPUT_DIR/anki-linux"

# Clean existing output directory
rm -rf "$LAUNCHER_DIR"

# Build binaries based on host architecture
if [ "$HOST_ARCH" = "aarch64" ]; then
    # On aarch64 host, only build for aarch64
    cargo build -p launcher --release --target aarch64-unknown-linux-gnu
else
    # On other hosts, build for both architectures
    cargo build -p launcher --release --target x86_64-unknown-linux-gnu
    CARGO_TARGET_AARCH64_UNKNOWN_LINUX_GNU_LINKER=aarch64-linux-gnu-gcc \
        cargo build -p launcher --release --target aarch64-unknown-linux-gnu
    # Extract uv_lin_arm for cross-compilation
    (cd ../../.. && ./ninja extract:uv_lin_arm)
fi

# Create output directory
mkdir -p "$LAUNCHER_DIR"

# Copy binaries and support files
TARGET_DIR=${CARGO_TARGET_DIR:-../../../target}

# Copy binaries with architecture suffixes
if [ "$HOST_ARCH" = "aarch64" ]; then
    # On aarch64 host, copy arm64 binary to both locations
    cp "$TARGET_DIR/aarch64-unknown-linux-gnu/release/launcher" "$LAUNCHER_DIR/launcher.amd64"
    cp "$TARGET_DIR/aarch64-unknown-linux-gnu/release/launcher" "$LAUNCHER_DIR/launcher.arm64"
    # Copy uv binary to both locations
    cp "../../../out/extracted/uv/uv" "$LAUNCHER_DIR/uv.amd64"
    cp "../../../out/extracted/uv/uv" "$LAUNCHER_DIR/uv.arm64"
else
    # On other hosts, copy architecture-specific binaries
    cp "$TARGET_DIR/x86_64-unknown-linux-gnu/release/launcher" "$LAUNCHER_DIR/launcher.amd64"
    cp "$TARGET_DIR/aarch64-unknown-linux-gnu/release/launcher" "$LAUNCHER_DIR/launcher.arm64"
    cp "../../../out/extracted/uv/uv" "$LAUNCHER_DIR/uv.amd64"
    cp "../../../out/extracted/uv_lin_arm/uv" "$LAUNCHER_DIR/uv.arm64"
fi

# Copy support files from lin directory
for file in README.md anki.1 anki.desktop anki.png anki.xml anki.xpm install.sh uninstall.sh anki; do
    cp "$file" "$LAUNCHER_DIR/"
done

# Copy additional files from parent directory
cp ../pyproject.toml "$LAUNCHER_DIR/"
cp ../../../.python-version "$LAUNCHER_DIR/"
cp ../versions.py "$LAUNCHER_DIR/"

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

ZSTD="zstd -c --long -T0 -18"
TRANSFORM="s%^.%anki-linux%S"
TARBALL="$OUTPUT_DIR/anki-linux.tar.zst"

tar -I "$ZSTD" --transform "$TRANSFORM" -cf "$TARBALL" -C "$LAUNCHER_DIR" .

echo "Build complete:"
echo "Universal launcher: $LAUNCHER_DIR"
echo "Tarball: $TARBALL"
