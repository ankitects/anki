#!/bin/bash

cd /Volumes/Two/anki
echo "cleaning up..."
rm -rf build dist
echo "syncing updates..."
rsync -av reflex:Lib/code/libanki --exclude .git --exclude build --exclude dist --delete .
rsync -av reflex:Lib/code/ankiqt --exclude .git --exclude build --exclude dist  --delete .
find . -name '*.pyc' -exec rm {} \;
echo "updating kakasi..."
mkdir -p kakasi
cp -Rvf /usr/local/bin/kakasi kakasi
cp -Rvf /usr/local/share/kakasi/kanwadict kakasi
cp -Rvf /usr/local/share/kakasi/itaijidict kakasi
# echo "updating audio..."
# mkdir -p audio
# cp -Rvf /usr/local/bin/lamex audio
echo "adding image formats..."
cp -Rvf imageformats ankiqt
echo "building..."
PYTHONPATH=ankiqt:libanki python ankiqt/mac/setup.py bdist_dmg

