#!/bin/bash

TOOLS="$(cd "`dirname "$0"`"; pwd)"
pylint -j 0 --rcfile=$TOOLS/../.pylintrc -f colorized --extension-pkg-whitelist=PyQt5 $TOOLS/../anki $TOOLS/../aqt
