#!/bin/bash

cd /Volumes/Two/anki
echo "syncing updates..."
rsync -av reflex:Lib/code/libanki --exclude .git --exclude build --exclude dist --delete .
rsync -av reflex:Lib/code/ankiqt --exclude .git --exclude build --exclude dist  --delete .
./ankiqt/anki
