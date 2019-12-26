#!/bin/bash
#
# Starts fuzz tests. Requires the 'pythonfuzz' package.
#
# Usage:
# tools/fuzz.sh  # run all fuzzers

set -e

BIN="$(cd "`dirname "$0"`"; pwd)"
export PYTHONPATH=${BIN}/..:${PYTHONPATH}

dir=.

(cd $dir && python fuzz/fuzz_all.py)
