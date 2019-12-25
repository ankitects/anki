#!/bin/bash
#
# Usage:
# tools/tests.sh  # run all tests
# tools/tests.sh decks # test only test_decks.py
# coverage=1 tools/tests.sh # run with coverage test

set -e

BIN="$(cd "`dirname "$0"`"; pwd)"
export PYTHONPATH=${BIN}/..:${PYTHONPATH}

nose="python -m nose2 --plugin=nose2.plugins.mp -N 16"

dir=.

if [ x$1 = x ]; then
    lim="tests"
else
    lim="tests.test_$1"
fi

(cd $dir && $nose $lim $args)
