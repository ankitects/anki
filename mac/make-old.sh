#!/bin/bash

cd /Volumes/Two/anki
if [ "x$debug" = "x" ]; then
    echo "cleaning up..."
    rm -rf build dist
fi
echo "syncing updates..."
rsync -av reflex:Lib/code/libanki --exclude .git --exclude build --exclude dist --delete .
rsync -av reflex:Lib/code/ankiqt --exclude .git --exclude build --exclude dist  --delete .
find . -name '*.pyc' -exec rm {} \;
echo "adding image formats..."
rm -rf ankiqt/imageformats
mkdir ankiqt/imageformats
cp -Rvf imageformats/libq{gif,jpeg,svg,tiff}* ankiqt/imageformats
echo "building..."
PYTHONPATH=ankiqt:libanki python ankiqt/mac/setup.py bdist_dmg

