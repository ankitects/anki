#!/bin/bash

set -e

test -f update.sh || {
  echo "run from release folder"
  exit 1
}

# Get the project root (two levels up from qt/release)
PROJ_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# Use extracted uv binary
UV="$PROJ_ROOT/out/extracted/uv/uv"

# Prompt for wheel version
read -p "Wheel version: " VERSION

# Export dependencies using uv
echo "Exporting dependencies..."
rm -f pyproject.toml
DEPS=$("$UV" export --no-hashes --no-annotate --no-header --extra audio --extra qt --all-packages --no-dev --no-emit-workspace)

# Generate the pyproject.toml file
cat > pyproject.toml << EOF
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
        echo "  \"$line\"," >> pyproject.toml
    fi
done

# Complete the pyproject.toml file
cat >> pyproject.toml << 'EOF'
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

echo "Generated pyproject.toml with version $VERSION"
