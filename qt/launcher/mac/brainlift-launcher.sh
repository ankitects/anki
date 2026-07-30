#!/bin/bash

set -euo pipefail

RESOURCES_DIR="$(cd "$(dirname "$0")/../Resources" && pwd)"
RUNTIME_DIR="$RESOURCES_DIR/runtime"
UV="$RESOURCES_DIR/uv"
BUILD_COMMIT="$(cat "$RESOURCES_DIR/brainlift-commit.txt")"
export ANKI_BRAINLIFT_COMMIT="$BUILD_COMMIT"
INSTALL_ROOT="${BRAINLIFT_INSTALL_ROOT:-$HOME/Library/Application Support/AnkiBrainlift}"
VENV="$INSTALL_ROOT/venv"
INSTALLED_COMMIT="$INSTALL_ROOT/installed-commit.txt"

mkdir -p "$INSTALL_ROOT"

if [[ ! -f "$INSTALLED_COMMIT" ]] || [[ "$(cat "$INSTALLED_COMMIT")" != "$BUILD_COMMIT" ]]; then
    rm -rf "$VENV"
fi

if [[ ! -x "$VENV/bin/python" ]]; then
    UV_PROJECT_ENVIRONMENT="$VENV" \
        UV_PYTHON_INSTALL_DIR="$INSTALL_ROOT/python" \
        UV_CACHE_DIR="$INSTALL_ROOT/cache" \
        UV_NO_PROGRESS=1 \
        "$UV" sync \
        --project "$RUNTIME_DIR" \
        --frozen \
        --managed-python \
        --python 3.13
    printf '%s\n' "$BUILD_COMMIT" > "$INSTALLED_COMMIT"
fi

if [[ "${BRAINLIFT_INSTALLER_SMOKE_ONLY:-0}" == "1" ]]; then
    "$VENV/bin/python" - "$BUILD_COMMIT" <<'PY'
import sys

from anki.collection import Collection
from aqt.brainlift import brainlift_dashboard
from aqt.identity import app_name, brainlift_commit

assert hasattr(Collection, "brainlift_score_snapshot")
assert callable(brainlift_dashboard)
assert brainlift_commit() == sys.argv[1]
assert app_name() == "Anki Brainlift"
print(f"Brainlift installer smoke passed for {sys.argv[1]}")
PY
    exit 0
fi

exec "$VENV/bin/python" -c \
    "import aqt, sys; sys.argv[0] = 'Anki Brainlift'; aqt.run()" \
    "$@"
