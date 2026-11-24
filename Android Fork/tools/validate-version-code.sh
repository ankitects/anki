#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-3.0-or-later
# Usage from the repository root: tools/validate-version-code.sh [base-commit]

set -euo pipefail

base=${1:-HEAD^}
if [[ "$base" =~ ^0+$ ]]; then
  base=HEAD^
fi

# Fetch the comparison commit if it is outside the shallow checkout.
if ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  git fetch --no-tags --depth=1 origin "$base"
fi

./gradlew :AnkiDroid:validateVersionCode -PversionCodeBase="$base" --daemon
