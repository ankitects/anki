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

# Read version from .version file
VERSION=$(cat "$PROJ_ROOT/.version" | tr -d '[:space:]')

# Copy existing pyproject.toml to .old if it exists
if [ -f pyproject.toml ]; then
    cp pyproject.toml pyproject.toml.old
fi

# Export dependencies using uv
echo "Exporting dependencies..."
rm -f pyproject.toml
DEPS=$(cd "$PROJ_ROOT" && "$UV" export --no-hashes --no-annotate --no-header --extra audio --extra qt --all-packages --no-dev --no-emit-workspace)

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

[build-system]
requires = ["hatchling"]
build-backend = "hatchling.build"

# hatch throws an error if nothing is included
[tool.hatch.build.targets.wheel]
include = ["no-such-file"]
EOF

echo "Generated pyproject.toml with version $VERSION"

# Show diff if .old file exists
if [ -f pyproject.toml.old ]; then
    echo
    echo "Differences from previous version:"
    diff -u --color=always pyproject.toml.old pyproject.toml || true
fi
