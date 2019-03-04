#!/bin/bash
#
# Usage:
# tools/tests.sh  # run all tests
# tools/tests.sh decks # test only test_decks.py
# coverage=1 tools/tests.sh # run with coverage test

BIN="$(cd "`dirname "$0"`"; pwd)"
export PYTHONPATH=${BIN}/..:${PYTHONPATH}

# favour nosetests3 if available
nose=nosetests
if which nosetests3 >/dev/null 2>&1; then
    nose=nosetests3
fi

dir=.

if [ x$1 = x ]; then
    lim="tests"
else
    lim="tests.test_$1"
fi

if [ x$coverage != x ]; then
    args="--with-coverage"
fi
(cd $dir && $nose -s --processes=16 --process-timeout=300 $lim $args --cover-package=anki)
