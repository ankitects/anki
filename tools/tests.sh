#!/bin/bash

if [ -d 'locale' ]; then
    dir=..
else
    dir=.
fi

if [ x$coverage != x ]; then
    args="--with-coverage"
else
    args=""
    echo "Call with coverage=1 to run coverage tests"
fi
(cd $dir && nosetests -vs $args --cover-package=anki $@)
