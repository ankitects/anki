#!/bin/bash
# Copyright: Ankitects Pty Ltd and contributors
# License: GNU AGPL, version 3 or later; http://www.gnu.org/licenses/agpl.html

set -e

# Get the project root directory
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJ_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Build the dylib first
echo "Building macOS helper dylib..."
"$PROJ_ROOT/out/pyenv/bin/python" "$SCRIPT_DIR/helper_build.py"

# Create the wheel using uv
echo "Creating wheel..."
cd "$SCRIPT_DIR"
"$PROJ_ROOT/out/extracted/uv/uv" build --wheel

echo "Build complete!"
