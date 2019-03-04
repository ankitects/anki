#!/bin/bash

TOOLS="$(cd "`dirname "$0"`"; pwd)"
pylint -E -j 0 -f colorized --extension-pkg-whitelist=PyQt5 $TOOLS/../anki $TOOLS/../aqt
