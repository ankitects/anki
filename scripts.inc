#!/bin/bash

set -e

CHECKABLE="rslib pylib qt"
DEVEL="rspy pylib qt"

if [ "$UNOPT" = "" ]; then
    BUILDFLAGS="--release --strip"
else
    BUILDFLAGS=""
fi

# activate venv
if [ ! -d pyenv ]; then
    python -m venv pyenv
    . pyenv/bin/activate
    pip install --upgrade pip setuptools
else
    . pyenv/bin/activate
fi

# add qt if missing
python -c 'import PyQt5' 2>/dev/null || pip install -r qt/requirements.qt

# update build hash
oldhash=$(test -f meta/buildhash && cat meta/buildhash || true)
newhash=$(git rev-parse --short HEAD)
if [ "$oldhash" != "$newhash" ]; then
    echo $newhash > meta/buildhash
fi
