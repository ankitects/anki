#!/bin/bash
set -e

# Get the project root (two levels up from qt/bundle)
PROJ_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# Use extracted uv binary
UV="$PROJ_ROOT/out/extracted/uv/uv"

# Prompt for wheel version
read -p "Wheel version: " VERSION

# Create release directory if it doesn't exist
mkdir -p release

# Export dependencies using uv
echo "Exporting dependencies..."
DEPS=$("$UV" export --no-hashes --no-annotate --no-header --extra audio --extra qt --all-packages --no-dev --no-emit-workspace)

# Generate the pyproject.toml file
cat > release/pyproject.toml << EOF
[project]
name = "anki-release"
version = "$VERSION"
description = "A package to lock Anki's dependencies"
requires-python = ">=3.9"
dependencies = [
  "anki==$VERSION",
  "aqt==$VERSION",
EOF

# Add the exported dependencies to the file
echo "$DEPS" | while IFS= read -r line; do
    if [[ -n "$line" ]]; then
        echo "  \"$line\"," >> release/pyproject.toml
    fi
done

# Complete the pyproject.toml file
cat >> release/pyproject.toml << 'EOF'
]

[[tool.uv.index]]
name = "testpypi"
url = "https://test.pypi.org/simple/"
publish-url = "https://test.pypi.org/legacy/"
explicit = true

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

# hatch throws an error if nothing is included
[tool.hatch.build.targets.wheel]
include = ["no-such-file"]
EOF

echo "Generated release/pyproject.toml with version $VERSION"
