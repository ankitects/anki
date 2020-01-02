#!/bin/bash

set -e

CHECKABLE="lib-rust lib-python anki-qt"
DEVEL="lib-rspy lib-python anki-qt i18n"

if [ "$UNOPT" = "" ]; then
    BUILDFLAGS="--release --strip"
else
    BUILDFLAGS=""
fi

# activate venv
test -d pyenv || python -m venv pyenv
. pyenv/bin/activate
pip install --upgrade pip setuptools

# add qt if missing
python -c 'import PyQt5' 2>/dev/null || pip install -r anki-qt/requirements.qt

# fetch i18n files
test -d i18n || git clone -depth 1 https://github.com/ankitects/anki-i18n i18n
