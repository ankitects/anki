#!/bin/bash

if [ -d 'locale' ]; then
    dir=..
else
    dir=.
fi
(cd $dir && nosetests -vs --with-coverage --cover-package=anki $@)
