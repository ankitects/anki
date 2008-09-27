#!/bin/bash

if [ -d 'locale' ]; then
    dir=..
else
    dir=.
fi
(cd $dir && nosetests -vs $@)
