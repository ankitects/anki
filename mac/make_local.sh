#!/bin/bash

PYTHON=/Library/Frameworks/Python.framework/Versions/2.6/bin/python2.6

if [ "x$debug" = "x" ]; then
    echo "cleaning up..."
    rm -rf build dist
fi
find . -name '*.pyc' -exec rm {} \;
echo "adding image formats..."
rm -rf ankiqt/imageformats
mkdir ankiqt/imageformats
cp -Rvf /Developer/Applications/Qt/plugins/imageformats/libq{gif,jpeg,svg,tiff}* ankiqt/imageformats
echo "building..."
PYTHONPATH=ankiqt:libanki $PYTHON ankiqt/mac/setup.py bdist_dmg

