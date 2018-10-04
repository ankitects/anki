#!/bin/bash
#
# Usage:
# tools/tests.sh  # run all tests
# tools/tests.sh decks # test only test_decks.py
# coverage=1 tools/tests.sh # run with coverage test

BIN="$(cd "`dirname "$0"`"; pwd)"
export PYTHONPATH=${BIN}/..:${PYTHONPATH}

# Default tools installation docs on Ubuntu results in "nosetests3" vs "nosetests"
NOSETESTS=nosetests
`which $NOSETESTS` > /dev/null 2>&1
if [ "$?" == 1 ]; then
    NOSETESTS=nosetests3
fi

dir=.

if [ x$1 = x ]; then
    lim="tests"
else
    lim="tests.test_$1"
fi

if [ x$coverage != x ]; then
    args="--with-coverage"
else
    args=""
    echo "Call with coverage=1 to run coverage tests"
fi
(cd $dir && $NOSETESTS -vs --processes=16 --process-timeout=300 $lim $args --cover-package=anki)
