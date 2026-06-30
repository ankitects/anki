#!/usr/bin/env bash
# MCAT fork: launcher for the 50k-card engine benchmark (spec §7h) on Unix.
# Mirrors bench.ps1. Invoked by `just bench`; can also be run directly.
set -euo pipefail
here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
export PYTHONPATH="$root/out/pylib"
export ANKI_TEST_MODE=1
py="$root/out/pyenv/bin/python"
deck="$here/fixtures/synthetic_50k.anki2"
if [ ! -f "$deck" ]; then
    "$py" "$here/make_synthetic_deck.py" --out "$deck"
fi
"$py" "$here/bench.py" --deck "$deck" "$@"
