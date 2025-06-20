#!/bin/bash

# Get the project root (two levels up from qt/release)
PROJ_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

# Use extracted uv binary
UV="$PROJ_ROOT/out/extracted/uv/uv"

rm -rf dist
"$UV" build --wheel

#UV_PUBLISH_TOKEN=$(pass show w/pypi-api-test) "$UV" publish --index testpypi
UV_PUBLISH_TOKEN=$(pass show w/pypi-api) "$UV" publish
