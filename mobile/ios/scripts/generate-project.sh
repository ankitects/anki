#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

command -v xcodegen >/dev/null 2>&1 || {
  echo "xcodegen is required" >&2
  exit 1
}

"$SCRIPT_DIR/generate-swift.sh"
"$SCRIPT_DIR/build-xcframework.sh" --simulator-only
xcodegen generate --spec "$IOS_ROOT/project.yml" --project "$IOS_ROOT"
